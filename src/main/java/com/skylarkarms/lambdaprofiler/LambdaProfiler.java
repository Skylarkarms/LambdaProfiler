package com.skylarkarms.lambdaprofiler;

import com.skylarkarms.concur.Executors;
import com.skylarkarms.concur.Locks;
import com.skylarkarms.numberutils.MedianCalculator;

import java.io.PrintStream;
import java.lang.invoke.VarHandle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LambdaProfiler {
    protected void onTime(StackTraceElement accessPoint, ProcessID id, long total) {}
    protected void delayed(StackTraceElement accessPoint, ProcessID id, long finish) {}
    protected void timeout(Thread thread, ProcessID id,
                           StackDatum init_datum, long startNanos, long timeout) {}

    public record ProcessID(String type, int execID, int prof_hash){

        static ProcessID generate(
                String type, int execID, int prof_hash
        ) {
            return new ProcessID(type, execID, prof_hash);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof LambdaProfilerImpl.TimeoutProcess pe){
                return pe.id.isEqualTo(this);
            } else throw new IllegalStateException("Special designation");
        }

        private boolean isEqualTo(ProcessID id) { return execID == id.execID && Objects.equals(type, id.type); }

        @Override
        public int hashCode() { return Objects.hash(type, execID); }

        @Override
        public String toString() {
            return "ID{"
                    + "\n  >> type='" + type
                    + "\n  >> execution order=" + execID
                    + "\n  >> profiler hash=" + prof_hash
                    + "\n}";
        }
    }

    public record StackDatum(String trace, StackTraceElement accessPoint){
        @Override
        public String toString() {
            return "AccessPoint{"
                    + "\n  >> trace=\n" + trace.indent(3)
                    + "  >> accessPoint=\n" + accessPoint.toString().indent(3)
                    + '}';
        }
    }

    private static final ThreadFactory defaultFact = Executors.cleanFactory(Thread.MAX_PRIORITY);
    private static volatile ThreadFactory mCustom = defaultFact;
    private static volatile boolean factoryGrabbed;
    public static void setCustom(ThreadFactory custom) {
        if (mCustom != defaultFact) throw new IllegalStateException("Instance can only be set once.");
        if (factoryGrabbed) throw new IllegalStateException("\n ThreadFactory has already been initialized. this method should be called before any one "
                + Interpreter.class + " instance or any of it's derived classes has been initialized.");
        mCustom = custom;
    }
    
    public interface LambdaFactory {
        ThreadFactoryProfiler wrap(
                StackWindow window,
                ThreadFactory factory
        );
        ThreadFactoryProfiler wrap(
                ThreadFactory factory
        );
        ExecutorProfiler.Based wrap(
                StackWindow window,
                Executors.BaseExecutor baseExecutor
        );
        /**
         * call_site set to default execution of this object's methods DIRECTLY
         * */
        ExecutorProfiler.Based wrap(Executors.BaseExecutor baseExecutor);
        ExecutorProfiler wrap(StackWindow window, Executor executor);
        /**
         * call_site set to default execution of this object's methods DIRECTLY
         * */
        ExecutorProfiler wrap(Executor executor);

        BooleanSupplier exceptional(BooleanSupplier core);
        <T>Supplier<T> exceptional(Supplier<T> core);
        Runnable exceptional(Runnable command);
    }

    record max_factory() {
        static {
            factoryGrabbed = true;
        }
        private static final ThreadFactory ref = mCustom;
    }

    private static final AtomicInteger
            staticBooleanSupplier_counter = new AtomicInteger(),
            staticRunnable_counter = new AtomicInteger(),
            staticSupplier_counter = new AtomicInteger(),
            staticBooleanSupplierID = new AtomicInteger(),
            staticRunnableID = new AtomicInteger(),
            staticSupplierID = new AtomicInteger()
                    ;

    public static String staticInspection() {
        return "Static calls"
                + "\n BooleanSupplier static calls = " + staticBooleanSupplier_counter
                + "\n Runnable static calls = " + staticRunnable_counter
                + "\n Supplier static calls = " + staticSupplier_counter;
    }

    private static final String
            STATIC_BOOL = "[BooleanSupplier - static call]"
            , STATIC_SUP = "[Supplier - static call]"
            , STATIC_RUN = "[Runnable - static call]";

    public static BooleanSupplier exceptional(
            BooleanSupplier command,
            long timeout_nanos,
            LambdaProfiler lambdaProfiler
    ) {
        return exceptional(
                2, StackWindow.first,
                command,
                timeout_nanos,
                lambdaProfiler
        );
    }

    public static BooleanSupplier exceptional(
            BooleanSupplier command,
            long duration, TimeUnit unit,
            LambdaProfiler lambdaProfiler
    ) {
        return exceptional(
                2, StackWindow.first,
                command,
                unit.toNanos(duration)
                , lambdaProfiler
        );
    }

    public static BooleanSupplier exceptional(
            BooleanSupplier command,
            StackWindow window,
            long duration, TimeUnit unit,
            LambdaProfiler lambdaProfiler
    ) {
        return exceptional(
                2, window,
                command,
                unit.toNanos(duration)
                , lambdaProfiler
        );
    }

    public static Runnable exceptional(
            Runnable command,
            long timeout_nanos,
            LambdaProfiler lambdaProfiler
    ) {
        return exceptional(
                2, StackWindow.first,
                command,
                timeout_nanos,
                lambdaProfiler
        );
    }

    public static Runnable exceptional(
            Runnable command,
            long duration, TimeUnit unit,
            LambdaProfiler lambdaProfiler
    ) {
        return exceptional(
                2, StackWindow.first,
                command,
                unit.toNanos(duration),
                lambdaProfiler
        );
    }

    public static Runnable exceptional(
            Runnable command,
            StackWindow window,
            long duration, TimeUnit unit,
            LambdaProfiler lambdaProfiler
    ) {
        return exceptional(
                2, window,
                command,
                unit.toNanos(duration),
                lambdaProfiler
        );
    }

    public static<T> Supplier<T> exceptional(
            Supplier<T> command,
            long timeout_nanos,
            LambdaProfiler lambdaProfiler
    ) {
        return exceptional(
                2, StackWindow.first,
                command,
                timeout_nanos,
                lambdaProfiler
        );
    }

    public static<T> Supplier<T> exceptional(
            Supplier<T> command,
            long duration, TimeUnit unit,
            LambdaProfiler lambdaProfiler
    ) {
        return exceptional(
                2, StackWindow.first,
                command
                , unit.toNanos(duration),
                lambdaProfiler
        );
    }

    public static<T> Supplier<T> exceptional(
            Supplier<T> command,
            StackWindow window,
            long duration, TimeUnit unit,
            LambdaProfiler lambdaProfiler
    ) {
        return exceptional(
                2, window,
                command
                , unit.toNanos(duration),
                lambdaProfiler
        );
    }

    private static BooleanSupplier exceptional(
            int call_site,
            StackWindow window,
            BooleanSupplier command,
            long timeout_nanos,
            LambdaProfiler lambdaProfiler

    ) {
        StackDatum element = getCallerStackTrace(call_site, window);
        return () -> {

            long start = System.nanoTime();
            ProcessID id = ProcessID.generate(STATIC_BOOL, staticBooleanSupplierID.incrementAndGet(), -1);
            final Locks.Valet val = countdown(
                    element, start, timeout_nanos, id, lambdaProfiler
            );
            boolean res =  command.getAsBoolean();
            shutdown(
                    element.accessPoint,
                    val, id, System.nanoTime(),
                    start,
                    lambdaProfiler
            );
            return res;
        };
    }

    private static final void shutdown(
            StackTraceElement e
            , Locks.Valet v,
            ProcessID id, long finish, long startnanos
            , LambdaProfiler lambdaProfiler
    ) {
        Boolean shut;
        if ((shut = v.shutdown()) == null || shut) {
            long total = finish - startnanos;
            lambdaProfiler.onTime(e, id, total);
        } else {
            lambdaProfiler.delayed(e, id, finish);
        }
    }

    private static final Locks.Valet countdown(
            StackDatum element,
            long startNanos,
            long timeout_nanos,
            ProcessID id,
            LambdaProfiler onFailed
    ) {
        assert onFailed != null;
        Thread thread = Thread.currentThread();
        Locks.Valet valet_ = new Locks.Valet();
        max_factory.ref.newThread(
                () -> {
                    // the timeout should deduct the time spent since the first time (startNanos) was loaded, and this line.
                    // timeout - (thisLine - starNanos) parenthesis prevents overflowing
                    Boolean success = valet_.parkShutdown(
                            timeout_nanos - (System.nanoTime() - startNanos)
                    );
                    assert success != null;
                    if (success) {
                        onFailed.timeout(
                                thread,
                                id,
                                element, startNanos, timeout_nanos
                        );
                    }
                }
        ).start();
        return valet_;
    }

    private static Runnable exceptional(
            int call_site,
            StackWindow window,
            Runnable command,
            long timeout_nanos,
            LambdaProfiler lambdaProfiler
    ) {
        StackDatum element = getCallerStackTrace(call_site, window);
        return () -> {
            long start = System.nanoTime();
            final ProcessID id = ProcessID.generate(STATIC_RUN, staticRunnableID.incrementAndGet(), -1);
            final Locks.Valet val = countdown(
                    element,
                    start, timeout_nanos, id, lambdaProfiler
            );
            VarHandle.fullFence();
            command.run();
            VarHandle.fullFence();
            shutdown(
                    element.accessPoint,
                    val,
                    id, System.nanoTime(), start,
                    lambdaProfiler
            );
        };
    }

    private static<T> Supplier<T> exceptional(
            int call_site,
            StackWindow window,
            Supplier<T> command,
            long timeout_nanos,
            LambdaProfiler lambdaProfiler
    ) {
        StackDatum element = getCallerStackTrace(call_site, window);
        return () -> {
            long start = System.nanoTime();
            final ProcessID id = ProcessID.generate(STATIC_SUP, staticSupplierID.incrementAndGet(), -1);

            final Locks.Valet val = countdown(
                    element, start, timeout_nanos, id, lambdaProfiler
            );
            VarHandle.fullFence();
            T t = command.get();
            VarHandle.fullFence();
            shutdown(
                    element.accessPoint,
                    val, id, System.nanoTime(), start,
                    lambdaProfiler
            );
            return t;
        };
    }

    private static final StackDatum getCallerStackTrace(int call_site, StackWindow window) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();

        int length = elements.length, start = -1;
        for (int i = 0; i < length; i++) {
            if (elements[i].getMethodName().equals("getStackTrace")) {
                start = i + 1 + call_site;
            }
        }

        if (start == -1) throw new IllegalStateException("Couldn't find getStackTrace in stack trace: " + Arrays.toString(elements));

        final int
                last_index = length - 1,
                trueS,
                startAt = window == StackWindow.last ? (trueS = last_index) : Math.min(start + (trueS = window.start), last_index),
                r,
                endAt = (r = window.rest) == -1 ? length : (startAt + r); //(will not account for "last index" to prevent `<=` and use only `<` on loop)

        assert endAt > startAt : "`endAt`[" + endAt + "] cannot be lesser than `startAt`[" + startAt + "]";
        assert length > startAt : "`startAt` index [" + startAt + "] greater than or equal to StackTraceElement arrays length [" + length  +"].";
        StringBuilder sb = new StringBuilder(((length - startAt) * 2) + 2);
        sb.append(" >> stack {");
        int last_endAt = Math.min(endAt, length); // min should be length (not last index)

        int finalL = length - start;
        int y = Math.min(trueS, finalL - 1);
        for (int i = startAt; i < last_endAt; i++) {
            sb.append("\n   - at [")
                    .append(y++)
                    .append("] ")
                    .append(elements[i]);
        }
        String res = sb.append("\n } << stack. [total length = ").append(finalL).append("]").toString();
        return new StackDatum(res, elements[last_index]);
    }

    public static final class Params {
        final StackWindow window;
        final Interpreter.INKS printer;
        final Executors.FixedScheduler.ScheduleParams params;
        final long timeout_nanos;
        public static final Params DEFAULT = new Params(
                StackWindow.first,
                Interpreter.INKS.DEFAULT(),
                Executors.FixedScheduler.ScheduleParams.TEN_SEC(),
                TimeUnit.SECONDS.toNanos(5)
        );

        /**
         * @see Executors.FixedScheduler.ScheduleParams#BASE_PERIODIC()
         * */
        public static final Params PERIODIC = new Params(
                StackWindow.first,
                Interpreter.INKS.DEFAULT(),
                Executors.FixedScheduler.ScheduleParams.BASE_PERIODIC(),
                TimeUnit.MILLISECONDS.toNanos(2500)
        );

        /**
         * @see Executors.FixedScheduler.ScheduleParams#BASE_PERIODIC()
         * */
        public static final Params FULL = new Params(
                StackWindow.all,
                Interpreter.INKS.DEFAULT(),
                Executors.FixedScheduler.ScheduleParams.FIFTEEN_SEC(),
                TimeUnit.SECONDS.toNanos(10)
        );

        Params(StackWindow window, Interpreter.INKS printer, Executors.FixedScheduler.ScheduleParams params, long timeout_nanos) {
            this.window = window;
            this.printer = printer;
            this.params = params;
            this.timeout_nanos = timeout_nanos;
        }
        Params(Params params, long timeout_nanos) {
            this.window = params.window;
            this.printer = params.printer   ;
            this.params = params.params;
            this.timeout_nanos = timeout_nanos;
        }
        public static final class Builder {
            StackWindow window;
            Interpreter.INKS printer;
            Executors.FixedScheduler.ScheduleParams params;
            long timeout_nanos;

            Builder(StackWindow window, Interpreter.INKS printer, Executors.FixedScheduler.ScheduleParams params, long timeout_nanos) {
                this.window = window;
                this.printer = printer;
                this.params = params;
                this.timeout_nanos = timeout_nanos;
            }

            Builder(Params params) {
                this(
                        params.window, params.printer, params.params, params.timeout_nanos
                );
            }

            public Builder setParams(Executors.FixedScheduler.ScheduleParams params) {
                this.params = params;
                return this;
            }

            public Builder setPrinter(Interpreter.INKS printer) {
                this.printer = printer;
                return this;
            }

            public Builder setTimeout_nanos(long timeout_nanos) {
                this.timeout_nanos = timeout_nanos;
                return this;
            }

            public Builder setTimeout(long duration, TimeUnit unit) {
                this.timeout_nanos = unit.toNanos(duration);
                return this;
            }

            public Builder setWindow(StackWindow window) {
                this.window = window;
                return this;
            }
        }

        public static Params custom(Consumer<Builder> builder) {
            if (builder == null) return Params.DEFAULT;
            else {
                Builder builder1 = new Builder(Params.DEFAULT);
                builder.accept(builder1);
                return new Params(
                        builder1.window,
                        builder1.printer,
                        builder1.params,
                        builder1.timeout_nanos
                );
            }
        }
        public static Params timeout(long duration, TimeUnit unit) {
            return new Params(Params.DEFAULT, unit.toNanos(duration));
        }
    }

    /**
     * Will define the {@link StackDatum#trace} field.
     * */
    public record StackWindow(int start, int rest){
        public static StackWindow first = new StackWindow(0, 1);
        public static StackWindow all = new StackWindow(0, -1);
        public static StackWindow last = new StackWindow(-2, -1);
        public static StackWindow truncate(int end) {
            if (end == 0) throw new IllegalStateException("value cannot be 0");
            return new StackWindow(0, end);
        }
        public static StackWindow begin(int at) { return new StackWindow(at, -1); }
        public static StackWindow single(int start) { return new StackWindow(start, 1); }
    }

    static class LambdaProfilerImpl extends LambdaProfiler implements LambdaFactory {

        @Override
        public ThreadFactoryProfiler wrap(
                StackWindow window,
                ThreadFactory factory
        ) {
            return new ThreadFactoryProfiler(
                    window,
                    factory, this);
        }

        @Override
        public ThreadFactoryProfiler wrap(ThreadFactory factory) {
            return new ThreadFactoryProfiler(factory, this);
        }

        @Override
        public ExecutorProfiler.Based wrap(
                StackWindow window,
                Executors.BaseExecutor baseExecutor
        ) {
            return new ExecutorProfiler.Based(
                    window,
                    baseExecutor, this);
        }

        /**
         * call_site set to default execution of this object's methods DIRECTLY
         * */
        @Override
        public ExecutorProfiler.Based wrap(Executors.BaseExecutor baseExecutor) {
            return new ExecutorProfiler.Based(baseExecutor, this);
        }

        @Override
        public ExecutorProfiler wrap(StackWindow window, Executor executor) {
            return new ExecutorProfiler(window, executor, this);
        }

        /**
         * call_site set to default execution of this object's methods DIRECTLY
         * */
        @Override
        public ExecutorProfiler wrap(Executor executor) {
            return new ExecutorProfiler(StackWindow.first, executor, this);
        }

        private LambdaProfilerImpl(
                StackWindow window,
                long timeout_nanos
        ) {
            this.window = window;
            this.timeout_nanos = timeout_nanos;
            this.factory = max_factory.ref;
        }

        public LambdaProfilerImpl(
                long timeout_nanos
        ) {
            this(
                    StackWindow.first,
                    timeout_nanos
            );
        }

        public LambdaProfilerImpl(long duration, TimeUnit unit) {
            this(
                    unit.toNanos(duration)
            );
        }

        // Modified TimeOutStatistic to use RunningMedianStatistic
        static final class TimeOutStatistic {
            private final MedianCalculator statistics;
            private long[] lastSnapshot;

            TimeOutStatistic() {
                this.statistics = new MedianCalculator();
                this.lastSnapshot = new long[]{0, 0, 0};
            }

            public void addTime(long time) {
                statistics.addValue(time);
                // Update snapshot
                lastSnapshot = statistics.getStatistics();
            }

            public long[] getTime() { return lastSnapshot; }

            public int getTimes() { return statistics.getCount(); }

            @Override
            public String toString() {
                return "TimeOutStatistic{" +
                        "\n >> stats =\n" + snap().indent(3) +
                        " >> count = " + statistics.getCount() +
                        "\n}";
            }

            String snap() {
                return
                        " - min = " + formatNanos(lastSnapshot[0])
                                + "\n - median = " + formatNanos(lastSnapshot[1])
                                + "\n - max = " + formatNanos(lastSnapshot[2]);
            }
        }

        final AtomicInteger
                booleanID = new AtomicInteger()
                , supplierID = new AtomicInteger()
                , runnableID = new AtomicInteger()
                , finished_boolean = new AtomicInteger()
                , finished_supplier = new AtomicInteger()
                , finished_runnable = new AtomicInteger()
                ;

        private static final String
                BOOL = "[BooleanSupplier process]"
                , RUN = "[Runnable process]"
                , SUP = "[Supplier process]"
                ;

        public int activeBooleanProcesses() { return booleanID.getOpaque() - finished_boolean.getOpaque(); }

        public int activeRunnableProcesses() { return runnableID.getOpaque() - finished_runnable.getOpaque(); }

        public int activeSupplierProcesses() { return supplierID.getOpaque() - finished_supplier.getOpaque(); }

        private final long timeout_nanos;
        private final StackWindow window;

        /*private */final int hash = hashCode();

        @Override
        public BooleanSupplier exceptional(BooleanSupplier core) { return exceptional(core, 3, window); }

        BooleanSupplier exceptional(BooleanSupplier core, int stackIndex, StackWindow window) {
            final StackDatum element = getCallerStackTrace(stackIndex, window);
            return () -> {
                ProcessID id = ProcessID.generate(
                        BOOL,
                        booleanID.incrementAndGet(),
                        hash
                );
                long startNano = System.nanoTime();
                Locks.Valet v = countdown(element, startNano, id);
                VarHandle.fullFence();
                boolean res =  core.getAsBoolean();
                VarHandle.fullFence();
                shutdown(v, element, id, System.nanoTime(), startNano);
                finished_boolean.incrementAndGet();
                return res;
            };
        }

        @Override
        public<T> Supplier<T> exceptional(Supplier<T> core) { return exceptional(core, 3, window); }

        <T>Supplier<T> exceptional(Supplier<T> core, int stackIndex, StackWindow window) {
            final StackDatum element = getCallerStackTrace(stackIndex, window);
            return () -> {
                ProcessID id = ProcessID.generate(SUP,
                        supplierID.incrementAndGet(),
                        hash);
                long startNano = System.nanoTime();
                Locks.Valet v =  countdown(element, startNano, id);
                VarHandle.fullFence();
                T res =  core.get();
                VarHandle.fullFence();
                shutdown(v, element, id, System.nanoTime(), startNano);
                finished_supplier.incrementAndGet();
                return res;
            };
        }

        @Override
        public Runnable exceptional(Runnable command) { return exceptional(command, 3, window); }

        final Runnable exceptional(Runnable command, int stackIndex, StackWindow window) {
            final StackDatum element = getCallerStackTrace(stackIndex, window);
            return () -> {
                ProcessID id = ProcessID.generate(RUN,
                        runnableID.incrementAndGet(),
                        hash);
                long startnanos = System.nanoTime();
                Locks.Valet v = countdown(element, startnanos, id);
                VarHandle.fullFence();
                command.run();
                VarHandle.fullFence();
                shutdown(v, element, id, System.nanoTime(), startnanos);
                finished_runnable.incrementAndGet();
            };
        }

        private final void shutdown(Locks.Valet v, StackDatum element, ProcessID id, long finish, long startnanos) {
            Boolean shut;
            if ((shut = v.shutdown()) == null || shut) {
                long total = finish - startnanos;
                onTime(element.accessPoint, id, total);
            } else {
                delayed(element.accessPoint, id, finish);
            }
        }

        final ThreadFactory factory;

        static final class TimeoutProcess {
            final Thread thread;
            final ProcessID id;
            final StackDatum stackDatum;
            final long starting_time;
            private volatile long ending_time; //Time of completion AFTER the exception had thrown.
            volatile long finished;

            public void setEnding_time(long ending_time) {
                this.ending_time = ending_time;
                finished = ending_time - starting_time;
            }

            final long timeout_nanos;

            TimeoutProcess(
                    Thread thread,
                    ProcessID id,
                    StackDatum stackDatum,
                    long starting_time,
                    long timeout_nanos
            ) {
                this.thread = thread;
                this.id = id;
                this.stackDatum = stackDatum;
                this.starting_time = starting_time;
                this.timeout_nanos = timeout_nanos;
            }

            @Override
            public String toString() {
                return "TimeoutProcess{"
                        + "\n >> thread=\n" + thread.toString().indent(3)
                        + " >> id=\n" + id.toString().indent(3)
                        + " >> access_point =\n" + stackDatum.toString().indent(3)
                        + " >> starting_time = " + starting_time
                        + ",\n >> timeout_nanos =\n" + formatNanos(timeout_nanos).indent(3)
                        + " >> finish time =\n" + (ending_time == 0 ? "[unfinished]" : formatNanos(finished)).indent(3)
                        + "}@".concat(Integer.toString(hashCode()));
            }
        }

        private final Locks.Valet countdown(
                StackDatum element,
                long startNanos,
                ProcessID id
        ) {
            Thread thread = Thread.currentThread();
            Locks.Valet valet_ = new Locks.Valet();
            factory.newThread(
                    () -> {
                        // the timeout should deduct the time spent since the first time (startNanos) was loaded, and this line.
                        // timeout - (thisLine - starNanos) parenthesis prevents overflowing
                        Boolean success = valet_.parkShutdown(
                                timeout_nanos - (System.nanoTime() - startNanos)
                        );
                        assert success != null;
                        if (success) {
                            timeout(
                                    thread, id,
                                    element, startNanos, timeout_nanos
                            );
                        }
                    }
            ).start();
            return valet_;
        }

        @Override
        public String toString() {
            return "Profiler{" +
                    (
                            "\n >> booleanProcesses=" + booleanID +
                                    "\n >> activeBooleanProcesses=" + activeBooleanProcesses() +
                                    "\n >> runnableProcesses=" + runnableID +
                                    "\n >> activeRunnableProcesses=" + activeRunnableProcesses() +
                                    "\n >> supplierProcesses=" + supplierID +
                                    "\n >> activeSupplierProcesses=" + activeSupplierProcesses() +
                                    "\n >> timeout nanos=\n" + formatNanos(timeout_nanos).indent(3)).indent(3) +
                    "}@".concat(Integer.toString(hashCode()));
        }
    }

    public static class Interpreter
            extends LambdaProfilerImpl
            implements Executors.Activator {

        static volatile boolean base_init;

        record Base() {
            static{base_init = true;}
            static final ConcurrentHashMap<String, Interpreter> ref = new ConcurrentHashMap<>();

            public static void put(String tag, Interpreter interpreter) {
                if (ref.putIfAbsent(
                        tag,
                        interpreter
                ) != null) throw new IllegalStateException("Tag [" + tag + "] already present.");
            }
//            static Interpreter remove(String tag) {
//                return ref.remove(
//                        tag
//                );
//            }
        }

        public static Interpreter get(String tag) {
            Interpreter res = base_init ? Base.ref.get(tag) : null;
            if (res == null) throw new NullPointerException("Interpreter not found for: " + tag);
            return res;
        }


        final ConcurrentHashMap<StackTraceElement, Queue<TimeoutProcess>> exceptionMap = new ConcurrentHashMap<>();
        // Map to store the highest-time entry for each StackTraceElement
        private final Map<StackTraceElement, TimeOutStatistic> execsMap = new ConcurrentHashMap<>();

        final Executors.Activator generator;

        volatile boolean isShutDown;

        public boolean isShutDown() {
            return isShutDown;
        }

        public void shutdown() {
            if (!isShutDown) {
                isShutDown = true;
                this.generator.stop();
                if (this.tag != null)
                    Base.ref.remove(tag);
                exceptionMap.clear();
                execsMap.clear();
            }

        }

        record Process(ProcessID id, long total) {
            @Override
            public String toString() {
                return "Process{" +
                        "\n >> id = \n" + id.toString().indent(3)
                        + " >> processing time =\n" + formatNanos(total).indent(3)
                        + "}";
            }
        }

        @Override
        protected final void onTime(StackTraceElement accessPoint, ProcessID id, long total) {
            execsMap.compute(accessPoint
                    , (ap, timeoutEntry) -> { //using the Map's monitor to alter the queue.
                        if (timeoutEntry == null) {
                            timeoutEntry = new TimeOutStatistic();
                        }
                        timeoutEntry.addTime(total);
                        return timeoutEntry;
                    }
            );
            this.mPrinter.accept(
                    DEBUG_TYPE.on_time, new Process(id, total)
            );
        }

        public String scanFailures() {
            long scan_time = System.nanoTime();
            StringBuilder builder = new StringBuilder(exceptionMap.size());
            builder.append(" >> Scan{");
            for (Map.Entry<StackTraceElement, Queue<TimeoutProcess>> e:exceptionMap.entrySet()
            ) {
                TimeoutProcess[] exceptions = e.getValue().toArray(TimeoutProcess[]::new);
                builder.append("\n   >> call site = \n")
                        .append(e.getKey().toString().indent(5))
                        .append("   >> erred for = ")
                        .append(exceptions.length);
                for (TimeoutProcess pe:exceptions
                ) {
//                    boolean finished = pe.finished != 0;
//                    long deadlock_t = finished ? pe.finished : (scan_time - pe.starting_time);
                    builder
                            .append("\n      >> exception = \n ")
                            .append(pe.toString().indent(8));
//                            .append("      >> deadlock time = \n ")
//                            .append(formatNanos(deadlock_t).concat(" (finished =" + finished + ")").indent(8));
                    if (pe.finished == 0) {
//                    if (!finished) {
                        builder
                                .append("      >> deadlock time = \n ")
                                .append(formatNanos(scan_time - pe.starting_time).indent(8));
                    }
                }
            }
            return builder.append("    }").toString();
        }

        public String scanSuccesses() {
            StringBuilder builder = new StringBuilder(execsMap.size() + 2);
            builder.append("ON_TIME: entries{");
            for (Map.Entry<StackTraceElement, TimeOutStatistic> e:execsMap.entrySet()
            ) {
                builder.append("\n stack = \n")
                        .append(e.getKey().toString().indent(3))
                        .append(" stats = \n")
                        .append(e.getValue().toString().indent(3));
            }
            return builder.append("\n}").toString();
        }

        @Override
        public boolean start() { return generator.start(); }

        @Override
        public boolean stop() { return generator.stop(); }

        @Override
        public boolean isActive() { return generator.isActive(); }

        public static final class INKS
        {
            final Consumer<String>[] inks;

            /**
             * Uses the {@link Consumer<String>} defaults defined at {@link DEBUG_TYPE} as template
             * */
            public static class Builder {
                final Consumer<String>[] inks = DEBUG_TYPE.DEFAULT_CACHE.ref;
                private static final Consumer<String> EMPTY = s -> {};


                public Builder set(DEBUG_TYPE type, Consumer<String> ink) {
                    inks[type.ordinal()] = ink;
                    return this;
                }
                public Builder set(
                        DEBUG_TYPE type1,
                        Consumer<String> ink1,
                        DEBUG_TYPE type2,
                        Consumer<String> ink2
                ) {
                    inks[type1.ordinal()] = ink1;
                    inks[type2.ordinal()] = ink2;
                    return this;
                }
                public Builder disable(
                        DEBUG_TYPE type
                ) {
                    inks[type.ordinal()] = EMPTY;
                    return this;
                }
                public Builder disableAll() {
                    Arrays.fill(inks, EMPTY);
                    return this;
                }

                public Builder set(
                        DEBUG_TYPE type1,
                        Consumer<String> ink1,
                        DEBUG_TYPE type2,
                        Consumer<String> ink2,
                        DEBUG_TYPE type3,
                        Consumer<String> ink3
                ) {
                    inks[type1.ordinal()] = ink1;
                    inks[type2.ordinal()] = ink2;
                    inks[type3.ordinal()] = ink3;
                    return this;
                }
                /**
                 * Will override previous actions setting previous types to `disabled`.
                 * */
                public void single(DEBUG_TYPE type, Consumer<String> ink) {
                    int break_p;
                    inks[break_p = type.ordinal()] = ink;
                    int i = 0;
                    for (; i < break_p; i++) {
                        inks[i] = EMPTY;
                    }
                    int l = inks.length;
                    for (i = break_p + 1; i < l; i++) {
                        inks[i] = EMPTY;
                    }
                }
            }
            @SuppressWarnings("unchecked")
            public static INKS single(
                    Consumer<String> for_all
            ) {
                Consumer<String>[] all = new Consumer[DEBUG_TYPE.values().length];
                Arrays.fill(all, for_all);
                return new INKS(all);
            }

            private INKS(Consumer<String>[] inks) { this.inks = inks; }

            private INKS(Builder builder) { this.inks = builder.inks; }

            public INKS(Consumer<Builder> builder) {
                Builder b = new Builder();
                builder.accept(b);
                this.inks = b.inks;
            }

            record Store() {
                static final INKS def = new INKS(DEBUG_TYPE.DEFAULT_CACHE.ref);
                static final INKS err = INKS.single(DEBUG_TYPE.pln.err);
                static final INKS out = INKS.single(DEBUG_TYPE.pln.out);
            }
            /**
             * @return default values for each {@link DEBUG_TYPE}
             * The {@link Consumer<String>}'s are set lazily, so the defaults can be configured a-priory via:
             * <ul>
             *     <li>{@link System#setOut(PrintStream)}</li>
             *     <li>{@link System#setErr(PrintStream)}</li>
             * </ul>
             *  - Defaults:
             * <ul>
             *     <li>{@link DEBUG_TYPE#delayed} = {@link System#err}</li>
             *     <li>{@link DEBUG_TYPE#timeout} = {@link System#err}</li>
             *     <li>{@link DEBUG_TYPE#scheduled} = {@link System#out}</li>
             *     <li>{@link DEBUG_TYPE#on_time} = {@link System#out}</li>
             * </ul>
             * */
            public static INKS DEFAULT() { return Store.def; }
            public static INKS OUT() { return Store.out; }
            public static INKS ERR() { return Store.err; }

            @Override
            public String toString() {
                String res = readInks(inks);
                return "INKS{" +
                        "\n  >> inks=\n" + res.indent(3) +
                        '}';
            }

            public static String readInks(Consumer<String>[] inks) {
                final int l;
                final StringBuilder res = new StringBuilder(2 + (l = inks.length));
                res.append("INKS{");
                DEBUG_TYPE[] vals = DEBUG_TYPE.values();
                for (int i = 0; i < l; i++) {
                    DEBUG_TYPE t = vals[i];
                    String name = t.name();
                    res.append("\n").append(
                            name.concat(", printer = \n").concat(inks[i].toString())
                    );
                }
                return res.append("\n}").toString();
            }
        }

        /**
         * Specifies the different messages delivered by this profiler.
         * Default values for each {@link DEBUG_TYPE} {@link Consumer<String>}'s
         * are set lazily, so, if the defaults need to be configured,
         * they can be done even after anyone enum has been called, BUT NOT BEFORE an {@link INKS} instance
         * and by extension a {@link Interpreter} instance, has been initialized:
         * <ul>
         *     <li>{@link System#setOut(PrintStream)}</li>
         *     <li>{@link System#setErr(PrintStream)}</li>
         * </ul>
         *  - Defaults:
         * <ul>
         *     <li>{@link DEBUG_TYPE#delayed} = {@link System#err}</li>
         *     <li>{@link DEBUG_TYPE#timeout} = {@link System#err}</li>
         *     <li>{@link DEBUG_TYPE#scheduled} = {@link System#out}</li>
         *     <li>{@link DEBUG_TYPE#on_time} = {@link System#out}</li>
         * </ul>
         * */
        public enum DEBUG_TYPE {
            delayed(
                    "[DELAYED]: ",
                    "Process finished at = \n"
                    ,() -> pln.err
            )
            , timeout(
                    "[TIMEOUT]: "
                    , "Process failed at = \n"
                    , () -> pln.err
            )
            , scheduled("[SCHEDULED]: "
                    , ""
                    , () -> pln.out
            )
            , on_time(
                    "[ON_TIME]: "
                    , " Process finished successfully = \n"
                    , () -> pln.out
            );
            final String
                    type,
                    message;
            final Supplier<Consumer<String>> defaultPrinter;

            DEBUG_TYPE(
                    String type
                    , String message
                    , Supplier<Consumer<String>> defaultPrinter
            ) {
                this.type = type;
                this.message = message;
                this.defaultPrinter = defaultPrinter;
            }

            record pln() {
                static final Consumer<String> out = new Consumer<>() {
                    @Override
                    public void accept(String x) {
                        System.out.println(x);
                    }

                    @Override
                    public String toString() {
                        return "[System.out]";
                    }
                };
                static final Consumer<String> err = new Consumer<>() {
                    @Override
                    public void accept(String x) {
                        System.err.println(x);
                    }

                    @Override
                    public String toString() {
                        return "[System.err]";
                    }
                };
            }

            record DEFAULT_CACHE() {
                static Consumer<String>[] ref = getDefaultInks();
            }

            @SuppressWarnings("unchecked")
            private static Consumer<String>[] getDefaultInks() {
                DEBUG_TYPE[] types = values();
                final int l;
                final Consumer<String>[] res = new Consumer[l = types.length];
                for (int i = 0; i < l; i++) {
                    res[i] = types[i].defaultPrinter.get();
                }
                return res;
            }

            @FunctionalInterface
            interface TypedPrinter {
                final class PrinterContainer {
                    final String tag;
                    final Consumer<String>[] printer;
                    final TypedPrinter typed;
                    public PrinterContainer(
                            String profiler_tag,
                            Consumer<String>[] printer) {
                        tag = " >> [".concat(profiler_tag).concat("]: \n");
                        this.printer = printer;
                        typed = TypedPrinter.generate(printer);
                    }

                    void accept(DEBUG_TYPE type, Object pe) { typed.accept(tag, printer, type, pe); }

                    @Override
                    public String toString() {
                        return "PrinterContainer{" +
                                "\n >> printer = \n" + INKS.readInks(printer).indent(3) +
                                '}';
                    }
                }
                void accept(String tag, Consumer<String>[] inks, DEBUG_TYPE type, Object pe);
                TypedPrinter NO_PROCESS = (tag, inks, type, pe) -> {};
                TypedPrinter process = (tag, inks, type1, pe) -> inks[type1.ordinal()].accept(
                        tag.concat(type1.apply(pe).indent(6))
                );
                static TypedPrinter generate(Consumer<String>[] inks) { return inks == null || allEmpty(inks) ? NO_PROCESS : process; }

                static boolean allEmpty(Consumer<String>[] cons) {
                    int l = cons.length;
                    for (int i = 0; i < l; i++) {
                        if (cons[i] != INKS.Builder.EMPTY) return false;
                    }
                    return true;
                }
            }

            String apply(Object pe) {
                return type.concat(message.concat(pe.toString().indent(3)));
            }
        }
        final DEBUG_TYPE.TypedPrinter.PrinterContainer mPrinter;
        final boolean activeScheduler;

        public Interpreter(
                Params params
        ) {
            this(
                    null, params
            );
        }

        public Interpreter(
                String tag,
                Params params
        ) {
            this(
                    tag, params.window, params.printer, params.params, params.timeout_nanos
            );
        }

        private record ScheduleFactory() {
            static final ThreadFactory ref = Executors.cleanFactory(Thread.NORM_PRIORITY);
        }

        private final String tag;

        Interpreter(
                String tag,
                StackWindow window,
                INKS printer,
                Executors.FixedScheduler.ScheduleParams params,
                long timeout_nanos
        ) {
            super(
                    window,
                    timeout_nanos
            );
            boolean nullTag;
            this.mPrinter = new DEBUG_TYPE.TypedPrinter.PrinterContainer(
                    !(nullTag = tag == null) ? tag : "Interpreter@".concat(Integer.toString(this.hash)), printer.inks);
            AtomicInteger scanNum = new AtomicInteger();
            activeScheduler = params != null
                    && printer.inks[DEBUG_TYPE.scheduled.ordinal()] != INKS.Builder.EMPTY;
            this.generator = activeScheduler ? Executors.FixedScheduler.generator(
                    ScheduleFactory.ref,
                    params,
                    () -> mPrinter.tag + "      [SCHEDULED]".concat(
                            " " + scanNum.incrementAndGet() + "/" +  (params.repetitions() + 1) + "\n" + scanFailures().indent(7)
                    ),
                    printer.inks[DEBUG_TYPE.scheduled.ordinal()]
//                    , params
            ) : Executors.Activator.default_activator;
            this.tag = tag;
            if (!nullTag) {
                Base.put(tag, this);
            }
        }

        @Override
        protected final void timeout(Thread thread, ProcessID id, StackDatum init_datum, long startNanos, long timeout) {
            exceptionMap.compute(
                    init_datum.accessPoint,
                    (element, e1) -> {
                        if (e1 == null) {
                            e1 = new PriorityQueue<>((o1, o2) -> Long.compare(o2.starting_time, o1.starting_time));
                            if (exceptionMap.size() == 0) {
                                if (activeScheduler) {
                                    mPrinter.printer[DEBUG_TYPE.scheduled.ordinal()].accept(
                                            mPrinter.tag
                                                    + "      [SCHEDULED ACTIVE]: Scheduled Scan..."
                                                    + "\n         params = \n" + ((Executors.FixedScheduler)generator).getParams().toString().indent(10)
                                    );
                                    generator.start();
                                }
                            }
                        }
                        final TimeoutProcess pe;
                        e1.add(
                                pe = new TimeoutProcess(thread, id, init_datum, startNanos, timeout)
                        );
                        mPrinter.accept(DEBUG_TYPE.timeout, pe);
                        return e1;
                    }
            );
        }

        @Override
        protected final void delayed(StackTraceElement accessPoint, ProcessID id, long finish) {
            TimeoutProcess[] pes = new TimeoutProcess[1];
            exceptionMap.computeIfPresent(
                    accessPoint,
                    (element, profiledExceptions) -> {
                        if (!profiledExceptions.isEmpty()) {
                            for (TimeoutProcess pe : profiledExceptions) {
                                if (id.isEqualTo(pe.id)) {
                                    pe.setEnding_time(finish);
                                    pes[0] = pe;
                                    break;
                                }
                            }
                        }
                        return profiledExceptions;
                    }
            );
            mPrinter.accept(
                    DEBUG_TYPE.delayed, pes[0]
            );
        }

        public Interpreter() {
            this(
                    Params.DEFAULT
            );
        }

        @Override
        public String toString() {
            String res = "Interpreter{" +
                    "\n  >> tag = " + tag;
            return res.concat(
                    isShutDown ?
                            "\n  >> [shutdown]\n}"
                            :
                            "\n  >> successes =\n" + scanSuccesses().indent(5) +
                                    "  >> failures =\n" + scanFailures().indent(5) +
                                    "  >> profiler =\n" + super.toString().indent(5) +
                                    "  >> generator =\n" + generator.toString().indent(5) +
                                    "  >> printer =\n" + mPrinter.toString().indent(5) +
                                    '}'
            );
//            return res;
        }
    }

    public static class ExecutorProfiler implements Executor {
        final StackWindow window; // This window will override the one from the profiler.
        final Executor executor;
        final LambdaProfilerImpl profilerImpl;

        public static class Based extends ExecutorProfiler implements Executors.BaseExecutor {
            final Executors.BaseExecutor executor;

            Based(
                    Executors.BaseExecutor executor,
                    LambdaProfilerImpl profilerImpl) {
                this(
                        profilerImpl.window,
                        executor, profilerImpl);
            }

            Based(
                    StackWindow window,
                    Executors.BaseExecutor executor,
                    LambdaProfilerImpl profilerImpl
            ) {
                super(
                        window,
                        executor, profilerImpl);
                this.executor = executor;
            }

            @Override
            public boolean onExecute(Runnable command) {
                return executor.onExecute(
                        profilerImpl.exceptional(command, 3, window)
                );
            }
        }

        ExecutorProfiler(
                StackWindow window,
                Executor executor,
                LambdaProfilerImpl profilerImpl
        ) {
            if (executor instanceof Based) throw new IllegalStateException("core Executor already instance of this class");
            this.window = window;
            this.executor = executor;
            this.profilerImpl = profilerImpl;
        }

        public ExecutorProfiler(
                StackWindow window,
                Executor executor,
                Params params
        ) {
            this(
                    window,
                    executor,
                    new Interpreter(params)
            );
        }

        public ExecutorProfiler(
                Executor executor,
                Params params
        ) {
            this(
                    params.window,
                    executor,
                    new Interpreter(params)
            );
        }

        public ExecutorProfiler(
                Executor executor
        ) {
            this(
                    executor,
                    Params.DEFAULT
            );
        }

        @Override
        public void execute(Runnable command) {
            executor.execute(
                    profilerImpl.exceptional(
                            command,
                            3, window
                    )
            );
        }

        @Override
        public String toString() {
            return "ExecutorProfiler{" +
                    "\n    >> executor=\n" + executor.toString().indent(3) +
                    "    >> profiler=" + profilerImpl.toString().indent(3)
                    + '}';
        }
    }

    public static class ThreadFactoryProfiler implements ThreadFactory {

        final StackWindow window;
        final ThreadFactory core;
        final LambdaProfilerImpl profilerImpl;

        ThreadFactoryProfiler(
                StackWindow window,
                ThreadFactory core, LambdaProfilerImpl profiler) {
            if (core instanceof ThreadFactoryProfiler) throw new IllegalStateException("core ThreadFactory already instance of this class.");
            this.window = window;
            this.core = core;
            this.profilerImpl = profiler;
        }

        ThreadFactoryProfiler(
                ThreadFactory core, LambdaProfilerImpl profiler) {
            this(profiler.window, core, profiler);
        }

        public ThreadFactoryProfiler(
                ThreadFactory core,
                Params params
        ) {
            this(params.window, core, new Interpreter(params));
        }

        public ThreadFactoryProfiler(
                ThreadFactory core
        ) {
            this(core, Params.DEFAULT);
        }

        @Override
        public Thread newThread(Runnable r) {
            return core.newThread(
                    profilerImpl.exceptional(r, 3, window)
            );
        }
    }

    static String formatNanos(long nanos) {
        // Break down nanos into seconds, milliseconds, and remaining nanoseconds
        long seconds = TimeUnit.NANOSECONDS.toSeconds(nanos);
        long millis = TimeUnit.NANOSECONDS.toMillis(nanos) % 1000;
        long remainingNanos = nanos % 1_000_000;

        // Build the formatted string
        return String.format("%d[seconds]: %03d[millis]: %03d[nanos]", seconds, millis, remainingNanos);
    }


}

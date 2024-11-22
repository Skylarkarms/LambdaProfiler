import com.skylarkarms.concur.Executors;
import com.skylarkarms.concur.Locks;
import com.skylarkarms.lambdaprofiler.LambdaProfiler;

import java.util.concurrent.TimeUnit;

public class BASE_TEST {
    public static void main(String[] args) {
        Executors.UNBRIDLED().execute(LambdaProfiler.exceptional(
                () -> {
                    Locks.robustPark(4, TimeUnit.SECONDS);
                },
                3, TimeUnit.SECONDS,
                new LambdaProfiler() {
                    final long[] tot = new long[1];
                    @Override
                    protected void timeout(Thread thread, ProcessID id, LambdaProfiler.StackDatum init_datum, long startNanos, long timeout) {
                        tot[0] = startNanos;
                        System.err.println("[timeout]: "
                                + "\n thread = " + thread
                                + "\n id =\n" + id.toString().indent(3)
                                + " init_datum = " + init_datum
                                + "\n startNanos = " + startNanos
                                + "\n timeout = " + timeout
                        );
                    }

                    @Override
                    protected void delayed(StackTraceElement accessPoint, LambdaProfiler.ProcessID id, long finish) {
                        System.err.println("[delayed]: "
                                + "\n accessPoint = " + accessPoint
                                + "\n id = " + id
                                + "\n finish = " + finish
                        );
                        System.out.println("Total = " + formatNanos(finish - tot[0]));
                    }

                    @Override
                    protected void onTime(StackTraceElement accessPoint, ProcessID id, long total) {
                        System.out.println("[onTime]: "
                                + "\n Total = " + formatNanos(total)
                                + "\n id = \n" + id.toString().indent(3)
                                + "\n acces point = " + accessPoint
                        );
                    }
                }
        ));
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


import com.skylarkarms.concur.Executors;
import com.skylarkarms.concur.Locks;
import com.skylarkarms.lambdaprofiler.LambdaProfiler;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

public class ExecutorsTest {
    static final LambdaProfiler.Interpreter INTERPRETER;
    static {
        System.setOut(new PrintStream(System.out, true));
        INTERPRETER = new LambdaProfiler.Interpreter(
                LambdaProfiler.Params.custom(
                        builder -> builder.setPrinter(LambdaProfiler.Interpreter.INKS.ERR())
                                .setParams(Executors.FixedScheduler.ScheduleParams.BASE_PERIODIC())
                                .setTimeout(100, TimeUnit.MILLISECONDS)
                )
        );
    }
    public static void main(String[] args) {
        Executors.BaseExecutor delayer =
                new Executors.Delayer(
                        INTERPRETER.wrap(
                                LambdaProfiler.StackWindow.all,
                                Executors.UNBRIDLED()
                        )
                , 4, TimeUnit.SECONDS
        )
                ;

        for (int i = 0; i < 10; i++) {
            Locks.robustPark(3, TimeUnit.SECONDS);
            int finalI = i;
            delayer.onExecute(
                    () -> {
                        System.out.println("Called = " + finalI);
                    }
            );
        }
    }
}

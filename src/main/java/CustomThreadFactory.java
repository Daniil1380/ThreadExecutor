import lombok.extern.java.Log;
import lombok.extern.log4j.Log4j;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Log
public class CustomThreadFactory implements ThreadFactory {
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, "MyPool-worker-" + threadCounter.incrementAndGet());
        thread.setUncaughtExceptionHandler((t, e) ->
                System.out.println("[ThreadFactory] Uncaught exception in thread " + t.getName() + ": " + e.getMessage())
        );
        return thread;
    }
}
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Log
public class CustomThreadPool implements CustomExecutor {

    // Параметры пула
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int minSpareThreads;
    private final int queueSize;
    private final RejectedExecutionHandlerImpl rejectionHandler;
    private final CustomThreadFactory threadFactory;

    // Состояние пула
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private final AtomicInteger idleThreads = new AtomicInteger(0);
    private volatile boolean isShutdown = false;

    // Список воркеров
    private final CopyOnWriteArrayList<Worker> workers = new CopyOnWriteArrayList<>();
    private AtomicInteger nextWorkerIndex = new AtomicInteger(0);

    public CustomThreadPool(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            TimeUnit timeUnit,
            int queueSize,
            int minSpareThreads,
            RejectedExecutionHandlerImpl rejectionHandler
    ) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.minSpareThreads = minSpareThreads;
        this.rejectionHandler = rejectionHandler;
        this.threadFactory = new CustomThreadFactory();
        this.queueSize = queueSize;

        // Инициализация базовых потоков
        for (int i = 0; i < corePoolSize; i++) {
            workers.add(addWorker());
            idleThreads.incrementAndGet();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            throw new IllegalStateException("ThreadPool is shut down");
        }

        // Используем round robin для выбора воркера
        Worker selectedWorker = null;
        for (int i = 0; i < workers.size(); i++) {
            int index = (nextWorkerIndex.getAndIncrement() + i) % workers.size();
            selectedWorker = workers.get(index);
            if (selectedWorker.offer(command)) {
                System.out.println("[Pool] Task assigned to worker: " + selectedWorker.getThread().getName() + ", hash=" + command.hashCode());
                ensureSpareThreads();
                return;
            }
        }

        // Если ни один воркер не принял задачу, применяем политику отказа
        rejectionHandler.rejectedExecution(command, this);
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> futureTask = new FutureTask<>(callable);
        execute(futureTask);
        return futureTask;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        // Даем возможность доработать текущим задачам
        System.out.println("[Pool] Shutdown initiated. Waiting for active tasks to complete.");
    }

    @Override
    public void shutdownNow() {
        isShutdown = true;
        // Прерываем все потоки и очищаем очередь
        for (Worker worker : workers) {
            worker.interrupt();
            worker.clear();
        }

        System.out.println("[Pool] Immediate shutdown. All tasks cancelled.");
    }

    // Создание нового воркера, если это возможно
    private Worker addWorker() {
        Worker worker = new Worker(queueSize);
        Thread thread = threadFactory.newThread(worker);
        worker.setThread(thread);
        thread.start();
        activeThreads.incrementAndGet();
        System.out.println("[ThreadFactory] Creating new thread: " + thread.getName());
        return worker;
    }

    // Проверка на необходимость создания дополнительных потоков
    private void ensureSpareThreads() {
        if (idleThreads.get() < minSpareThreads && activeThreads.get() < maxPoolSize && !isShutdown) {
            workers.add(addWorker());
        }
    }

    // Внутренний класс воркера
    private class Worker implements Runnable {

        @Setter
        @Getter
        private Thread thread;
        private final BlockingQueue<Runnable> taskQueue;

        private Worker(int queueCapacity) {
            this.taskQueue = new ArrayBlockingQueue<>(queueCapacity);
        }

        @Override
        public void run() {
            Runnable task;
            while (!isShutdown) {
                try {
                    // Ждем задачу с таймаутом keepAliveTime
                    task = taskQueue.poll(keepAliveTime, timeUnit);
                    if (task != null) {
                        // Задача получена — выполняем
                        idleThreads.decrementAndGet();
                        System.out.println("[Worker] " + Thread.currentThread().getName() + " executes hash=" + task.hashCode());
                        task.run();
                        idleThreads.incrementAndGet();
                    } else {
                        // Таймаут бездействия — завершаем поток, если их больше corePoolSize
                        if (activeThreads.get() > corePoolSize) {
                            activeThreads.decrementAndGet();
                            System.out.println("[Worker] " + Thread.currentThread().getName() + " idle timeout, stopping.");
                            workers.remove(this);
                            return;
                        }
                    }
                } catch (InterruptedException e) {
                    interrupt();
                }
            }
            // Если пул в shutdown — завершаем поток
            activeThreads.decrementAndGet();
            System.out.println("[Worker] " + Thread.currentThread().getName() + " terminated.");
            workers.remove(this);
        }

        public void interrupt() {
            thread.interrupt();
            System.out.println("[Worker] " + Thread.currentThread().getName() + " interrupted.");
            workers.remove(this);
        }

        public void clear() {
            taskQueue.clear();
        }

        public boolean offer(Runnable task) {
            return taskQueue.offer(task);
        }

    }

}
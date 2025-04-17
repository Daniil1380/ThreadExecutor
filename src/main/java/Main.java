import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        // Создаем пул с параметрами
        CustomExecutor pool = new CustomThreadPool(
                5,       // corePoolSize
                10,      // maxPoolSize
                60,      // keepAliveTime
                TimeUnit.SECONDS,
                20,     // queueSize
                0,       // minSpareThreads
                new RejectedExecutionHandlerImpl()
        );

        // Отправляем задачи
        for (int i = 0; i < 100; i++) {
            int taskId = i;
            pool.execute(() -> {
                System.out.println("Task " + taskId + " started by " + Thread.currentThread().getName());
                try {
                    Thread.sleep(10000); // Имитация работы
                } catch (InterruptedException e) {
                }
                System.out.println("Task " + taskId + " finished by " + Thread.currentThread().getName());
            });
        }

        pool.shutdown();
    }
}
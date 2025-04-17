import lombok.extern.java.Log;

import java.util.concurrent.RejectedExecutionException;


@Log
public class RejectedExecutionHandlerImpl  {

    public void rejectedExecution(Runnable r, CustomExecutor executor) {
        System.out.println("[Rejected] Task " + r + " was rejected due to overload!");
        throw new RejectedExecutionException("Task " + r + " rejected from " + executor);
    }
}
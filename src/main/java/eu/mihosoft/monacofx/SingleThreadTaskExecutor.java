package eu.mihosoft.monacofx;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SingleThreadTaskExecutor implements TaskExecutor {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor() ;

    @Override
    public void addTask(Runnable task) {
        executorService.submit(task);
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    @Override
    public void addInitTask(Runnable initCallback) {

    }
}

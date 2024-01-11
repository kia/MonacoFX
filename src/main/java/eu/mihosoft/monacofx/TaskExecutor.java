package eu.mihosoft.monacofx;

public interface TaskExecutor {
    void addTask(Runnable task);

    void shutdown();
}

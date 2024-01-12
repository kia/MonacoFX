package eu.mihosoft.monacofx;

public interface TaskExecutor {
    void addTask(Runnable task);

    void shutdown();

    void addInitTask(Runnable initCallback);
}

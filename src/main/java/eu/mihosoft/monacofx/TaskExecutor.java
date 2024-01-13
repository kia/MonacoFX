package eu.mihosoft.monacofx;

public interface TaskExecutor {
    void addTask(Runnable task);

    void start();

    void shutdown();

}

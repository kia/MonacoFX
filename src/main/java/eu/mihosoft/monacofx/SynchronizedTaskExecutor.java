package eu.mihosoft.monacofx;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SynchronizedTaskExecutor implements TaskExecutor {
    /** The logger used for all log records. */
    private static final Logger LOGGER = Logger.getLogger(SynchronizedTaskExecutor.class.getName());
    private final LinkedBlockingDeque<Runnable> linkedBlockingQueue =  new LinkedBlockingDeque<>();
    private final AtomicBoolean shutDownRequested = new AtomicBoolean(false);
    private final Object lock = new Object();
    private volatile boolean initialized = false;

    public SynchronizedTaskExecutor() {
        Thread thread = new Thread(() -> {
            while (!shutDownRequested.get()) {
                Runnable task = null;
                synchronized (lock) {
                    // Wait until there's a task to process
                    while (linkedBlockingQueue.isEmpty()) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            LOGGER.log(Level.INFO, e.getMessage(), e);
                            Thread.currentThread().interrupt();
                        }
                    }
                    if (initialized) {
                        task = linkedBlockingQueue.poll();
                    }
                }
                if(task != null) {
                    task.run();
                }
            }
        });
        thread.start();
    }
    @Override
    public void addTask(Runnable runnable) {
        synchronized (lock) {
            // Add the task to the queue
            linkedBlockingQueue.add(runnable);
            // Notify the processing thread that there's a new task
            lock.notify();
        }
    }

    @Override
    public void start() {
        this.initialized = true;
    }

    @Override
    public void shutdown() {
        shutDownRequested.set(true);
    }


}

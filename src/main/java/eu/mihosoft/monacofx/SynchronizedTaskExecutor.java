package eu.mihosoft.monacofx;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class SynchronizedTaskExecutor implements TaskExecutor {
    private final LinkedBlockingDeque<Task> linkedBlockingQueue =  new LinkedBlockingDeque<>();
    private final AtomicBoolean shutDownRequested = new AtomicBoolean(false);
    private final Object lock = new Object();
    private volatile boolean initialized = false;

    public SynchronizedTaskExecutor() {
        Thread thread = new Thread(() -> {
            while (!shutDownRequested.get()) {
                Task task;
                synchronized (lock) {
                    // Wait until there's a task to process
                    while (linkedBlockingQueue.isEmpty()) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            e.printStackTrace();
                        }
                    }
                    // check the first element in the queue. if it is an initializer then continue
                    if (initialized) {
                        task = linkedBlockingQueue.poll();
                    } else {
                        task = linkedBlockingQueue.getFirst();
                        if (task.isInitializer()) {
                            task = linkedBlockingQueue.removeFirst();
                            initialized = true;
                        }
                    }
                }
                if (initialized && task != null) {
                    if (task.isInitializer()) {
                        System.out.println("run isInitializer task = " + task + " " + this);
                    } else {
                        System.out.println("run task = " + task + " " + this);
                    }
                    task.getRunnable().run();
                }

            }
        });
        thread.start();
    }
    @Override
    public void addTask(Runnable runnable) {
        synchronized (lock) {
            // Add the task to the queue
            Task task = new Task();
            task.setRunnable(runnable);
            task.setInit(false);
            linkedBlockingQueue.add(task);
            // Notify the processing thread that there's a new task
            lock.notify();
        }
    }

    @Override
    public void shutdown() {
        shutDownRequested.set(true);
    }

    @Override
    public void addInitTask(Runnable runnable) {

        synchronized (lock) {
            // Add the task to the queue
            Task task = new Task();
            task.setInit(true);
            task.setRunnable(runnable);
            linkedBlockingQueue.addFirst(task);
            // Notify the processing thread that there's a new task
            lock.notify();
        }
    }

}

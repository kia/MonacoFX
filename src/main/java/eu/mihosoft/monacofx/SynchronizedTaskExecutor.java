package eu.mihosoft.monacofx;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SynchronizedTaskExecutor implements TaskExecutor {
    private final LinkedBlockingQueue<Runnable> linkedBlockingQueue =  new LinkedBlockingQueue<>();
    private final Map<Integer, Runnable> counterQueue = Collections.synchronizedMap(new HashMap<>());
    private final AtomicBoolean shutDownRequested = new AtomicBoolean(false);
    private final Object lock = new Object();
    private AtomicInteger counterAdded = new AtomicInteger(0);
    private AtomicInteger counterTaken = new AtomicInteger(0);
    public SynchronizedTaskExecutor() {
        Thread thread = new Thread(() -> {
            while (!shutDownRequested.get()) {
                Runnable task;
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
                    task = linkedBlockingQueue.poll();
                }
                task.run();

                Runnable checkTask = counterQueue.get(counterTaken.getAndIncrement());
                System.out.println("checkTask = " + checkTask);
                System.out.println("task      = " + task);
                if (!Objects.equals(checkTask,task)) {
                    System.out.println("tasks order is wrong!!!");
                }
            }
        });
        thread.start();
    }
    @Override
    public void addTask(Runnable task) {
        synchronized (lock) {
            // Add the task to the queue
            linkedBlockingQueue.add(task);
            counterQueue.put(counterAdded.getAndIncrement(), task);
            // Notify the processing thread that there's a new task
            lock.notify();
        }
    }

    @Override
    public void shutdown() {
        shutDownRequested.set(true);
    }

}

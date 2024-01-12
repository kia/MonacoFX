package eu.mihosoft.monacofx;

public class Task {
    private boolean isInit;
    private Runnable runnable;

    public boolean isInitializer() {
        return isInit;
    }

    public void setInit(boolean init) {
        isInit = init;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    public void setRunnable(Runnable runnable) {
        this.runnable = runnable;
    }
}

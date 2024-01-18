/*
 * MIT License
 *
 * Copyright (c) 2020-2022 Michael Hoffer <info@michaelhoffer.de>. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package eu.mihosoft.monacofx;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Worker;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Callback;
import netscape.javascript.JSObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This class, in the first place, is responsible for loading and initializing the monaco editor by performing
 * JavaScript code in JavaFX WebView.
 * It provides also some convenience method for changing the status of the editor by applying JavaScript functions.
 * It ensures that the web worker has loaded the page before any JavaScript task can be executed.
 * The order of execution of the JavaScripts tasks will remain the same as requested.
 */
public abstract class MonacoFX extends Region {

    private static final Logger LOGGER = Logger.getLogger(MonacoFX.class.getName());
    public static final int WAITING_INTERVALL = 500;
    public static final int MAX_NUMBER_OF_JS_EXECUTION_ATTEMPTS = 30;
    private final WebView view;
    private final WebEngine engine;
    private final static String EDITOR_HTML_RESOURCE_LOCATION = "/eu/mihosoft/monacofx/monaco-editor/index.html";
    private final Editor editor;
    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler = (t, e) -> {
        LOGGER.log(Level.SEVERE, "Unhandled Exception in thread \"" + t.getName() + "\"", e);
        e.printStackTrace(System.err);
        throw new RuntimeException(e);
    };;
    private volatile boolean readOnly;
    private BooleanProperty loadSucceeded = new SimpleBooleanProperty(false);

    private final Set<AbstractEditorAction> addedActions = Collections.synchronizedSet(new HashSet<>());
    /**
     * The newSingleThreadExecutor is used to add JavaScript tasks which has to be executed in order.
     */
    private final ExecutorService executorService;

    public MonacoFX() {
        view = new WebView();
        getChildren().add(view);
        engine = view.getEngine();
        String resourceNotFoundMsg = String.format("Missing resource '%s'. Editor cannot be loaded.", EDITOR_HTML_RESOURCE_LOCATION);
        String url = Objects.requireNonNull(getClass().getResource(EDITOR_HTML_RESOURCE_LOCATION),resourceNotFoundMsg).toExternalForm();

        editor = new Editor(engine);

        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(getUncaughtExceptionHandler());
            return thread;
        });
        Runnable initCallback = createInitCallback();
        load(url, initCallback);

        addFocusListener();
    }

    public void setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }

    public Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return uncaughtExceptionHandler;
    }
    public void setReadonlyMessage(String msg) {
        submitJavaScript(String.format("setReadonlyMessage('%s')", msg));
    }
    /**
     * implementation of close could be different in subclasses.
     */
    abstract public void close();

    public void shutdown() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
    };

    public int getScrollHeight() {
        return (int) submitJavaScript("editorView.getScrollHeight()");
    }

    public void addLineAtCurrentPosition(String text) {
        submitJavaScript("addTextAtCurrentPosition('" + text + "');");
    }
    @Override
    public void requestFocus() {
        view.requestFocus();
    }

    @Override protected double computePrefWidth(double height) {
        return view.prefWidth(height);
    }

    @Override protected double computePrefHeight(double width) {
        return view.prefHeight(width);
    }

    @Override public void requestLayout() {
        super.requestLayout();
    }

    @Override protected void layoutChildren() {
        super.layoutChildren();

        layoutInArea(view,0,0,getWidth(), getHeight(),
                0, HPos.CENTER, VPos.CENTER
        );
    }

    public Editor getEditor() {
        return editor;
    }

    @Deprecated
    public WebEngine getWebEngine() {
        return engine;
    }

    /**
     * Custom actions can be included in context menu of the editor.
     *
     * @param action {@link eu.mihosoft.monacofx.AbstractEditorAction} call back object as abstract action.
     */
    public void addContextMenuAction(AbstractEditorAction action) {
        if (!readOnly || action.isVisibleOnReadonly() ) {
            submitJavaScript(o -> {
                log("register action " + action.getActionId());
                if (!addedActions.contains(action)) {
                    JSObject window = (JSObject) engine.executeScript("window");
                    if (window != null) {
                        window.setMember(action.getName(), action);
                        String script = createAddContextMenuScript(action);
                        engine.executeScript(script);
                        addedActions.add(action);
                    }
                }
                return null;
            });
        }
    }

    public void removeContextMenuActionById(String actionId) {
        submitJavaScript(String.format("removeAction('%s');", actionId));
    }
    public void removeContextMenuAction(AbstractEditorAction action) {
        if (addedActions.contains(action)) {
            removeActionObject(action);
            addedActions.remove(action);
        }
    }

    public void removeContextMenuActions() {
        addedActions.forEach(this::removeActionObject);
        addedActions.clear();
    }
    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadonly(boolean readOnly) {
        submitJavaScript(String.format("setReadonly(%s)", readOnly));
        this.readOnly = readOnly;
    }
    public void setOption(String optionName, Object value) {
        submitJavaScript(String.format("editorView.updateOptions({ %s: %s })", optionName, value));
    }

    public Object submitJavaScript(String script) {
        log("submit javascript code: " + script);
        return submitJavaScript(param -> executeJavaScript(script));
    }

    public boolean isLoadSucceeded() {
        return loadSucceeded.get();
    }

    public BooleanProperty loadSucceededProperty() {
        return loadSucceeded;
    }

    // PRIVATE METHODS

    /**
     * This method calls the focus on monaco editor as soon as the focus is requested from the javafx side
     */
    private void addFocusListener() {
        view.focusedProperty().addListener((observableValue, aBoolean, focused) -> {
            if (focused) {
                submitJavaScript("editorView.focus();");
            }
        });
    }
    /**
     * loads the page and registers a listener which sets the loadSucceeded flag.
     *
     * @param url
     * @param initCallback
     */
    private void load(String url, Runnable initCallback) {
        log("submit init callback!");
        executorService.execute(initCallback);
        ClipboardBridge clipboardBridge = new ClipboardBridge(getEditor().getDocument(), new SystemClipboardWrapper());
        engine.getLoadWorker().stateProperty().addListener((observableValue, state, newState) -> {
            if (Worker.State.SUCCEEDED == newState) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("clipboardBridge", clipboardBridge);
                window.setMember("javaBridge", this);
                loadSucceeded.set(true);
            }
        });
        engine.load(url);
    }

    private Runnable createInitCallback() {
        AtomicBoolean jsDone = new AtomicBoolean(false);
        AtomicInteger attempts = new AtomicInteger();
        return () -> {
            long startTime = System.currentTimeMillis();
            while (!jsDone.get()) {
                try {
                    Thread.sleep(WAITING_INTERVALL);
                } catch (InterruptedException e) {
                    LOGGER.log(Level.SEVERE, e.getMessage(), e);
                    return;
                }
                if (loadSucceeded.get() && !jsDone.get()) {
                    Platform.runLater(() -> {
                        JSObject window = (JSObject) engine.executeScript("window");
                        Object jsEditorObj = window.call("getEditorView");
                        if (jsEditorObj instanceof JSObject) {
                            editor.setEditor(window, (JSObject) jsEditorObj);
                            jsDone.set(true);
                        }
                    });
                }
                if (attempts.getAndIncrement() > MAX_NUMBER_OF_JS_EXECUTION_ATTEMPTS) {
                    String msg = "Cannot initialize editor (JS execution not complete). Max number of attempts reached.";
                    throw new RuntimeException(msg);
                }
            }
            long endTime = System.currentTimeMillis();
            log("consumed time executing javascript code for init: " + (endTime - startTime));
        };
    }
    private void removeActionObject(AbstractEditorAction action) {
        removeContextMenuActionById(action.getActionId());
        JSObject window = (JSObject) executeJavaScript("window");
        window.removeMember(action.getName());
    }

    private void log(String msg) {
        if (Boolean.parseBoolean(System.getProperty("javascript.log"))) {
            LOGGER.log(Level.INFO, msg);
        }
    }

    private String createAddContextMenuScript(AbstractEditorAction action) {
        String precondition = "null";
        if (!action.isVisibleOnReadonly() && readOnly) {
            precondition = "\"false\"";
        }
        String actionName = action.getName();
        String keyBindings = Arrays.stream(action.getKeyBindings()).collect(Collectors.joining(","));
        String contextMenuOrder = "";
        if (action.getContextMenuOrder() != null && !action.getContextMenuOrder().isEmpty()) {
            contextMenuOrder = "contextMenuOrder: " + action.getContextMenuOrder() + ",\n";
        }

        String script = "editorView.addAction({\n" +
                "id: \"" + action.getActionId() + "\",\n" +
                "label: \"" + action.getLabel() + "\",\n" +
                "contextMenuGroupId: \"" + action.getContextMenuGroupId() + "\",\n" +
                "precondition: " + precondition + ",\n" +
                "keybindings: [" + keyBindings + "],\n" +
                contextMenuOrder +
                "run: (editor) => {" +
                actionName + ".action();\n" +
                action.getRunScript() +
                "}\n" +
                "});";
        return script;
    }
    private Object submitJavaScript(Callback<Object, Object> callback) {
        AtomicReference<Object> returnObject = new AtomicReference<>(null);
        if (!executorService.isShutdown()) {
            executorService.execute(() -> {
                if (!loadSucceeded.get()) {
                    throw new RuntimeException("Execution of JavaScript before init!");
                }
                Platform.runLater(() -> returnObject.set(callback.call(null)));
            });
        }
        return returnObject.get();
    }

    /**
     * This method should be called only as a callback in {@code #submitJavaScript(javafx.util.Callback)}
     * @param script
     * @return
     */
    private Object executeJavaScript(String script) {
        log("executing javascript code: " + script);
        if (script != null && !script.isEmpty()) {
            return engine.executeScript(script);
        }
        return null;
    }

}

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

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.concurrent.Worker;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.robot.Robot;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Callback;
import javafx.util.Duration;
import netscape.javascript.JSException;
import netscape.javascript.JSObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MonacoFX extends Region {

    private static final Logger LOGGER = Logger.getLogger(MonacoFX.class.getName());

    private final WebView view;
    private final WebEngine engine;

    private final static String EDITOR_HTML_RESOURCE_LOCATION = "/eu/mihosoft/monacofx/monaco-editor/index.html";

    private final Editor editor;
    private final SystemClipboardWrapper systemClipboardWrapper;
    private boolean readOnly;

    private Worker.State workerState;

    private Set<AbstractEditorAction> addedActions;

    public MonacoFX() {
        view = new WebView();
        addedActions = Collections.synchronizedSet(new HashSet<>());
        getChildren().add(view);
        engine = view.getEngine();
        String url = getClass().getResource(EDITOR_HTML_RESOURCE_LOCATION).toExternalForm();

        editor = new Editor(engine);

        systemClipboardWrapper = new SystemClipboardWrapper();
        ClipboardBridge clipboardBridge = new ClipboardBridge(getEditor().getDocument(), systemClipboardWrapper);
        engine.getLoadWorker().stateProperty().addListener((o, old, state) -> {
            workerState = state;
            if (state == Worker.State.SUCCEEDED) {
                AtomicBoolean jsDone = new AtomicBoolean(false);
                AtomicInteger attempts = new AtomicInteger();
                Thread thread = new Thread(() -> {
                    while (!jsDone.get()) {
                        // check if JS execution is done.
                        Platform.runLater(() -> {
                            JSObject window = (JSObject) engine.executeScript("window");
                            window.setMember("clipboardBridge", clipboardBridge);
                            window.setMember("javaBridge", this);
                            Object jsEditorObj = window.call("getEditorView");
                            if (jsEditorObj instanceof JSObject) {
                                editor.setEditor(window, (JSObject) jsEditorObj);
                                jsDone.set(true);
                            }
                        });

                        if(attempts.getAndIncrement()> 10) {
                            throw new RuntimeException(
                                "Cannot initialize editor (JS execution not complete). Max number of attempts reached."
                            );
                        }
                        if (!jsDone.get()) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                thread.start();
            }
        });
        engine.load(url);
        waitForSucceededWorkerState();
    }

//    /**
//     * implementation of close could be different in subclasses.
//     */
//    abstract public void close();
    /**
     * wait for succeeded state of the load worker
     */
    private void waitForSucceededWorkerState() {
        Timeline oneSecondWonder = new Timeline();
        EventHandler<ActionEvent> actionEventEventHandler = (ActionEvent event) -> {
            if (Worker.State.SUCCEEDED == workerState) {
                oneSecondWonder.stop();
            }
        };
        KeyFrame keyFrame = new KeyFrame(Duration.seconds(1), actionEventEventHandler);
        oneSecondWonder.getKeyFrames().setAll(keyFrame);
        oneSecondWonder.setCycleCount(15);
        oneSecondWonder.play();
    }

    public void reload() {
        engine.reload();
        setReadonly(isReadOnly());
    }

    private Object getSelectionObject() {
        return engine.executeScript("editorView.getModel().getValueInRange(editorView.getSelection())");
    }

    private static void pressArrowKey(Robot r, KeyCode keyCode, int count) {
        for (int i = 0; i <count; i++) {
            r.keyPress(keyCode);
        }
        r.keyRelease(keyCode);
    }

    @Override
    public void requestFocus() {
        executeJavaScriptLambda(null, param -> {
            super.requestFocus();
            getWebEngine().executeScript("setTimeout(() => {  editorView.focus();}, 1200);");
            return null;
        });
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
     * The call back implementation which is added for a custom action will be included in context menu of the editor.
     *
     * @param action {@link eu.mihosoft.monacofx.AbstractEditorAction} call back object as abstract action.
     */
    public void addContextMenuAction(AbstractEditorAction action) {
        if (!readOnly || action.isVisibleOnReadonly() ) {
            executeJavaScriptLambda(action, param -> {
                if (!addedActions.contains(action)) {
                    doAddContextMenuAction(action);
                }
                return null;
            });
        }
    }

    public void removeContextMenuActionById(String actionId) {
        String script = String.format("removeAction('%s');", actionId);
        executeJavaScriptLambda("", param -> {
            getWebEngine().executeScript(script);
            return null;
        });
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
    private void removeActionObject(AbstractEditorAction action) {
        removeContextMenuActionById(action.getActionId());
        JSObject window = (JSObject) getWebEngine().executeScript("window");
        window.removeMember(action.getName());
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadonly(boolean readOnly) {
        this.readOnly = readOnly;
        setOption("readOnly", readOnly);
    }

    public void setOption(String optionName, Object value) {
        String script = String.format("editorView.updateOptions({ " + optionName + ": %s })", value);
        executeJavaScriptLambda(script, param -> {
            getWebEngine().executeScript(script);
            return null;
        });
    }

    private void doAddContextMenuAction(AbstractEditorAction action) {
        String precondition = "null";
        if(!action.isVisibleOnReadonly() && readOnly) {
            precondition = "\"false\"";
        }
        JSObject window = (JSObject) getWebEngine().executeScript("window");
        String actionName = action.getName();
        String keyBindings = Arrays.stream(action.getKeyBindings()).collect(Collectors.joining(","));
        window.setMember(actionName, action);
        String contextMenuOrder = "";
        if (action.getContextMenuOrder() != null && !action.getContextMenuOrder().isEmpty()) {
            contextMenuOrder = "contextMenuOrder: " + action.getContextMenuOrder() + ",\n";
        }
        try {
            getWebEngine().executeScript(
                    "editorView.addAction({\n" +
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
                        "});"
            );
            addedActions.add(action);
        } catch (JSException exception) {
            LOGGER.log(Level.SEVERE, exception.getMessage());
        }

    }

    private void executeJavaScriptLambda(Object parameter , Callback<Object, Void> callback) {
        ReadOnlyObjectProperty<Worker.State> stateProperty = getWebEngine().getLoadWorker().stateProperty();
        if (Worker.State.SUCCEEDED == stateProperty.getValue()) {
            callback.call(parameter);
        } else {
            waitForSucceededWorkerState();
            AtomicBoolean jsDone = new AtomicBoolean(false);
            AtomicInteger attempts = new AtomicInteger();
            Thread thread = new Thread(() -> {
                while (!jsDone.get()) {
                    // check if JS execution is done.
                    Platform.runLater(() -> {
                        JSObject window = (JSObject) engine.executeScript("window");
                        Object jsEditorObj = window.call("getEditorView");
                        if (jsEditorObj instanceof JSObject) {
                            callback.call(parameter);
                            jsDone.set(true);
                        }
                    });
                    if (attempts.getAndIncrement() > 10) {
                        throw new RuntimeException(
                                "Cannot initialize editor (JS execution not complete). Max number of attempts reached."
                        );
                    }
                    if (!jsDone.get()) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            thread.start();

        }
    }
}

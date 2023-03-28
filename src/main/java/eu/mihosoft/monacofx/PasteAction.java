package eu.mihosoft.monacofx;

import netscape.javascript.JSObject;

public class PasteAction extends AbstractEditorAction {

    public PasteAction() {
        setLabel("Paste");
        setName("Paste");
        setActionId("editor.action.custom.clipboardPasteAction");
        setContextMenuOrder("3");
        setContextMenuGroupId("9_cutcopypaste");
        setVisibleOnReadonly(false);
        setKeyBindings(
                "monaco.KeyMod.Shift | monaco.KeyCode.Insert",
                "monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyV");
        setRunScript("let position = editor.getPosition();\n"
                + "let newPosition = clipboardBridge.paste(editor.getSelection(), position);\n"
                + "editor.setPosition(newPosition);\n"
                + "editor.focus();"
                + "let cursorPosition = this.editor.getPosition();\n"
                + "editor.setPosition(cursorPosition);");
    }

    @Override
    public void action() {
        // empty
    }
}

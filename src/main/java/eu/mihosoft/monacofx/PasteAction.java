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
        setRunScript("paste(editor);");
    }

    @Override
    public void action() {
        // empty
    }
}

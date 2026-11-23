package dev.anton.pulsing;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;

import java.util.HashMap;
import java.util.Map;

/**
 * Application-level registry that attaches an {@link EditorPulse} to every
 * regular editor and disposes it when the editor is released.
 */
public final class PulsingService implements Disposable {

    private final Map<Editor, EditorPulse> pulses = new HashMap<>();

    public static PulsingService getInstance() {
        return ApplicationManager.getApplication().getService(PulsingService.class);
    }

    public void attach(Editor editor) {
        if (editor.isDisposed() || editor.getProject() == null) {
            return;
        }
        if (editor.getEditorKind() != EditorKind.MAIN_EDITOR) {
            return;
        }
        if (pulses.containsKey(editor)) {
            return;
        }

        pulses.put(editor, new EditorPulse(editor));
    }

    public void detach(Editor editor) {
        EditorPulse pulse = pulses.remove(editor);
        if (pulse != null) {
            pulse.dispose();
        }
    }

    /** Applies a settings change to already running editors. */
    public void refreshAll() {
        for (EditorPulse pulse : pulses.values()) {
            pulse.reloadStyle();
        }
    }

    @Override
    public void dispose() {
        for (EditorPulse pulse : pulses.values()) {
            pulse.dispose();
        }
        pulses.clear();
    }
}

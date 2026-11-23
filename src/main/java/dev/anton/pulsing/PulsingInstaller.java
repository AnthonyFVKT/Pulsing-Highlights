package dev.anton.pulsing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import org.jetbrains.annotations.NotNull;

/**
 * Registered through the {@code editorFactoryListener} extension point, so the
 * platform creates it at plugin startup, before any editor exists.
 */
public final class PulsingInstaller implements EditorFactoryListener {

    public PulsingInstaller() {
        Application application = ApplicationManager.getApplication();
        application.invokeLater(() -> {
            if (application.isDisposed()) {
                return;
            }
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                PulsingService.getInstance().attach(editor);
            }
        }, ModalityState.any());
    }

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        PulsingService.getInstance().attach(event.getEditor());
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        PulsingService.getInstance().detach(event.getEditor());
    }
}

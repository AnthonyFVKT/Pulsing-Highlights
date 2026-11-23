package dev.anton.pulsing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * Application-wide settings of the plugin.
 *
 * <p>Kept deliberately as a plain bean with public fields so that the
 * IntelliJ XML serializer can persist it without extra annotations.</p>
 */
@State(name = "PulsingHighlightsSettings", storages = @Storage("pulsing-highlights.xml"))
public final class PulsingSettings implements PersistentStateComponent<PulsingSettings.State> {

    public static final class State {
        /** Master switch. */
        public boolean enabled = true;

        /** Pulse warnings too. */
        public boolean includeWarnings = true;

        /** Pulse weak warnings too. */
        public boolean includeWeakWarnings = false;

        /** Pulse the whole line instead of only the erroneous range. */
        public boolean wholeLine = true;

        /** Extra fast flashing for ~1.5 s after a problem appears. */
        public boolean flashOnNewError = true;

        /** Draw a colored frame around the editor based on the worst problem in the file. */
        public boolean borderEnabled = true;

        /** Frame width in pixels. */
        public int borderWidth = 2;

        /** Error color, 0xRRGGBB. Field name kept for backwards compatibility. */
        public int colorRgb = 0xFF3B30;

        /** Warning color, 0xRRGGBB. */
        public int warningColorRgb = 0xFFC107;

        /** Weak warning color, 0xRRGGBB. */
        public int weakWarningColorRgb = 0xB58900;

        /** Maximum background opacity, 0..255. */
        public int maxAlpha = 96;

        /** Breathing period in milliseconds. */
        public int periodMs = 1400;
    }

    private State state = new State();

    public static PulsingSettings getInstance() {
        return ApplicationManager.getApplication().getService(PulsingSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }
}

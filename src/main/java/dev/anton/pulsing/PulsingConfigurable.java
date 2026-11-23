package dev.anton.pulsing;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.ColorPanel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Color;

/**
 * Settings | Editor | Error Pulse.
 */
public final class PulsingConfigurable implements SearchableConfigurable {

    private JCheckBox enabledCheckBox;
    private JCheckBox warningsCheckBox;
    private JCheckBox weakWarningsCheckBox;
    private JCheckBox wholeLineCheckBox;
    private JCheckBox flashCheckBox;
    private JCheckBox borderCheckBox;
    private JSpinner borderWidthSpinner;

    private ColorPanel errorColorPanel;
    private ColorPanel warningColorPanel;
    private ColorPanel weakWarningColorPanel;

    private JSlider alphaSlider;
    private JSpinner periodSpinner;
    private JPanel panel;

    @Override
    public @NotNull String getId() {
        return "dev.anton.pulsing.settings";
    }

    @Override
    public String getDisplayName() {
        return "Pulsing Highlights";
    }

    @Override
    public @Nullable JComponent createComponent() {
        enabledCheckBox = new JCheckBox("Enable animated error pulse");
        warningsCheckBox = new JCheckBox("Pulse warnings (yellow)");
        weakWarningsCheckBox = new JCheckBox("Pulse weak warnings");
        wholeLineCheckBox = new JCheckBox("Pulse the whole line (more visible)");
        flashCheckBox = new JCheckBox("Flash brightly for ~1.5 s when a problem appears");
        borderCheckBox = new JCheckBox("Draw a colored frame around the editor (worst problem in the file)");
        borderWidthSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));

        errorColorPanel = new ColorPanel();
        warningColorPanel = new ColorPanel();
        weakWarningColorPanel = new ColorPanel();

        alphaSlider = new JSlider(8, 220, 96);
        alphaSlider.setMajorTickSpacing(53);
        alphaSlider.setPaintTicks(true);
        periodSpinner = new JSpinner(new SpinnerNumberModel(1400, 300, 6000, 100));

        JLabel alphaHint = new JLabel("(0 = invisible, 255 = opaque)");

        panel = FormBuilder.createFormBuilder()
                .addComponent(enabledCheckBox)
                .addComponent(warningsCheckBox)
                .addComponent(weakWarningsCheckBox)
                .addComponent(wholeLineCheckBox)
                .addComponent(flashCheckBox)
                .addComponent(borderCheckBox)
                .addLabeledComponent("Frame width, px:", borderWidthSpinner)
                .addLabeledComponent("Error color:", errorColorPanel)
                .addLabeledComponent("Warning color:", warningColorPanel)
                .addLabeledComponent("Weak warning color:", weakWarningColorPanel)
                .addLabeledComponent("Maximum opacity:", alphaSlider)
                .addComponent(alphaHint)
                .addLabeledComponent("Pulse period, ms:", periodSpinner)
                .addComponentFillVertically(new JPanel(new BorderLayout()), 0)
                .getPanel();

        reset();
        return panel;
    }

    @Override
    public boolean isModified() {
        PulsingSettings.State state = PulsingSettings.getInstance().getState();

        if (enabledCheckBox.isSelected() != state.enabled) {
            return true;
        }
        if (warningsCheckBox.isSelected() != state.includeWarnings) {
            return true;
        }
        if (weakWarningsCheckBox.isSelected() != state.includeWeakWarnings) {
            return true;
        }
        if (wholeLineCheckBox.isSelected() != state.wholeLine) {
            return true;
        }
        if (flashCheckBox.isSelected() != state.flashOnNewError) {
            return true;
        }
        if (borderCheckBox.isSelected() != state.borderEnabled) {
            return true;
        }
        if (((Number) borderWidthSpinner.getValue()).intValue() != state.borderWidth) {
            return true;
        }
        if (colorChanged(errorColorPanel, state.colorRgb)
                || colorChanged(warningColorPanel, state.warningColorRgb)
                || colorChanged(weakWarningColorPanel, state.weakWarningColorRgb)) {
            return true;
        }
        if (alphaSlider.getValue() != state.maxAlpha) {
            return true;
        }

        return ((Number) periodSpinner.getValue()).intValue() != state.periodMs;
    }

    private static boolean colorChanged(ColorPanel panel, int stateRgb) {
        Color color = panel.getSelectedColor();
        return color != null && (color.getRGB() & 0xFFFFFF) != stateRgb;
    }

    @Override
    public void apply() {
        PulsingSettings.State state = PulsingSettings.getInstance().getState();

        state.enabled = enabledCheckBox.isSelected();
        state.includeWarnings = warningsCheckBox.isSelected();
        state.includeWeakWarnings = weakWarningsCheckBox.isSelected();
        state.wholeLine = wholeLineCheckBox.isSelected();
        state.flashOnNewError = flashCheckBox.isSelected();
        state.borderEnabled = borderCheckBox.isSelected();
        state.borderWidth = ((Number) borderWidthSpinner.getValue()).intValue();

        state.colorRgb = selectedRgb(errorColorPanel, state.colorRgb);
        state.warningColorRgb = selectedRgb(warningColorPanel, state.warningColorRgb);
        state.weakWarningColorRgb = selectedRgb(weakWarningColorPanel, state.weakWarningColorRgb);

        state.maxAlpha = alphaSlider.getValue();
        state.periodMs = ((Number) periodSpinner.getValue()).intValue();

        PulsingService.getInstance().refreshAll();
    }

    private static int selectedRgb(ColorPanel panel, int fallback) {
        Color color = panel.getSelectedColor();
        return color != null ? color.getRGB() & 0xFFFFFF : fallback;
    }

    @Override
    public void reset() {
        PulsingSettings.State state = PulsingSettings.getInstance().getState();

        enabledCheckBox.setSelected(state.enabled);
        warningsCheckBox.setSelected(state.includeWarnings);
        weakWarningsCheckBox.setSelected(state.includeWeakWarnings);
        wholeLineCheckBox.setSelected(state.wholeLine);
        flashCheckBox.setSelected(state.flashOnNewError);
        borderCheckBox.setSelected(state.borderEnabled);
        borderWidthSpinner.setValue(state.borderWidth);

        errorColorPanel.setSelectedColor(new Color(state.colorRgb));
        warningColorPanel.setSelectedColor(new Color(state.warningColorRgb));
        weakWarningColorPanel.setSelectedColor(new Color(state.weakWarningColorRgb));

        alphaSlider.setValue(state.maxAlpha);
        periodSpinner.setValue(state.periodMs);
    }

    @Override
    public void disposeUIResources() {
        enabledCheckBox = null;
        warningsCheckBox = null;
        weakWarningsCheckBox = null;
        wholeLineCheckBox = null;
        flashCheckBox = null;
        borderCheckBox = null;
        borderWidthSpinner = null;
        errorColorPanel = null;
        warningColorPanel = null;
        weakWarningColorPanel = null;
        alphaSlider = null;
        periodSpinner = null;
        panel = null;
    }
}

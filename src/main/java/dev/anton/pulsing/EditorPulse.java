package dev.anton.pulsing;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import javax.swing.border.Border;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Animates error highlighting for a single editor.
 *
 * <p>Every {@link #TICK_MS} ms a Swing timer (EDT) re-reads the daemon
 * highlighters of this editor, extracts the ones that are real problems
 * (severity {@code ERROR}, optionally {@code WARNING}) and updates a
 * translucent background of its own highlighter according to a sine wave.</p>
 *
 * <p>Important: identifier-usages highlighting ("element under caret") uses a
 * synthetic severity {@code ERROR + 1}, which is greater than {@link HighlightSeverity#ERROR}.
 * A naive {@code severity >= ERROR} check would therefore pulse every occurrence
 * of the symbol under the caret. That is why only exact severities are accepted.</p>
 */
public final class EditorPulse implements Disposable {

    private static final int TICK_MS = 40;
    private static final long RESCAN_MS = 200;
    private static final long FLASH_MS = 1500;
    private static final double FLASH_PULSE_MS = 300.0;
    private static final int MAX_RANGES = 3000;
    // Draw above everything the daemon/other plugins produce, so the pulse is always visible.
    private static final int LAYER = HighlighterLayer.LAST - 1;

    private static final int ERROR_VALUE = HighlightSeverity.ERROR.myVal;
    private static final int WARNING_VALUE = HighlightSeverity.WARNING.myVal;
    private static final int WEAK_WARNING_VALUE = HighlightSeverity.WEAK_WARNING.myVal;

    private static final Logger LOG = Logger.getInstance(EditorPulse.class);

    private final Editor editor;
    private final Timer timer;
    private final Map<Long, PulseItem> active = new HashMap<>();

    private long lastScan = 0L;

    @Nullable
    private Border originalBorder;
    private boolean borderInstalled;
    private boolean borderFailed;
    private int installedBorderWidth = -1;
    private int installedBorderRgb = Integer.MIN_VALUE;

    private static final class PulseItem {
        final int start;
        final int end;
        final long birth;
        final int severityValue;
        @Nullable
        RangeHighlighterEx highlighter;

        PulseItem(int start, int end, long birth, int severityValue) {
            this.start = start;
            this.end = end;
            this.birth = birth;
            this.severityValue = severityValue;
        }
    }

    public EditorPulse(Editor editor) {
        this.editor = editor;
        this.timer = new Timer(TICK_MS, e -> tick());
        this.timer.setRepeats(true);
        this.timer.start();
    }

    private void tick() {
        if (editor.isDisposed()) {
            dispose();
            return;
        }

        JComponent component = editor.getContentComponent();
        if (!component.isShowing()) {
            return;
        }

        PulsingSettings.State settings = PulsingSettings.getInstance().getState();
        if (!settings.enabled) {
            if (!active.isEmpty()) {
                removeAll();
            }
            updateBorder(settings);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastScan >= RESCAN_MS) {
            lastScan = now;
            rescan(settings, now);
        }

        if (active.isEmpty()) {
            updateBorder(settings);
            return;
        }

        for (PulseItem item : active.values()) {
            RangeHighlighterEx highlighter = item.highlighter;
            if (highlighter == null || !highlighter.isValid()) {
                continue;
            }

            int alpha = alphaFor(settings, item, now);
            // Changing attributes makes the editor repaint the affected lines on its own
            // (EditorImpl.onHighlighterChanged -> repaintLines), no manual repaint needed.
            highlighter.setTextAttributes(attributes(colorFor(settings, item.severityValue), alpha));
        }

        updateBorder(settings);
    }

    /**
     * Collects all errors of the document and reconciles our highlighters with them.
     *
     * <p>Since 2025.x daemon highlighters (annotations, inspections) live in the
     * <b>document</b> markup model, not in {@code editor.getMarkupModel()}. That is why
     * the editor model only contains a handful of editor-local highlighters. The supported
     * way to read document highlights is {@link DaemonCodeAnalyzerEx#processHighlights}.</p>
     */
    private void rescan(PulsingSettings.State settings, long now) {
        Project project = editor.getProject();
        if (project == null || project.isDisposed()) {
            return;
        }

        Document document = editor.getDocument();
        int docLength = document.getTextLength();
        Map<Long, PulseItem> found = new HashMap<>();
        Set<Integer> rejectedSeverities = new HashSet<>();
        int[] total = {0};

        ReadAction.run(() -> {
            DaemonCodeAnalyzerEx.processHighlights(
                    document, project, null, 0, docLength, info -> {
                        total[0]++;
                        if (!matches(settings, info.getSeverity())) {
                            if (rejectedSeverities.size() < 20) {
                                rejectedSeverities.add(info.getSeverity().myVal);
                            }
                            return true;
                        }
                        addRange(found, info, now, docLength);
                        return found.size() < MAX_RANGES;
                    });

            // Belt and braces: editor-local highlighters that are not stored in the document model.
            for (RangeHighlighter highlighter : editor.getMarkupModel().getAllHighlighters()) {
                if (!highlighter.isValid()) {
                    continue;
                }
                total[0]++;
                HighlightInfo info = HighlightInfo.fromRangeHighlighter(highlighter);
                if (info != null && matches(settings, info.getSeverity())) {
                    addRange(found, info, now, docLength);
                }
            }
        });

        if (LOG.isDebugEnabled()) {
            LOG.debug("Pulsing Highlights [" + document + "]: highlighters=" + total[0]
                    + ", pulsing=" + found.size() + ", rejected severities=" + rejectedSeverities);
        }

        List<Long> stale = new ArrayList<>();
        for (Long key : active.keySet()) {
            if (!found.containsKey(key)) {
                stale.add(key);
            }
        }
        for (Long key : stale) {
            PulseItem item = active.remove(key);
            if (item != null) {
                removeHighlighter(item);
            }
        }

        for (PulseItem item : found.values()) {
            if (item.highlighter == null || !item.highlighter.isValid()) {
                item.highlighter = createHighlighter(item, settings);
            }
        }

        active.clear();
        active.putAll(found);
    }

    private void addRange(Map<Long, PulseItem> found, HighlightInfo info, long now, int docLength) {
        int start = info.getActualStartOffset();
        int end = info.getActualEndOffset();
        if (start < 0 || end <= start || found.size() >= MAX_RANGES) {
            return;
        }
        // File-level annotations use a fake highlighter spanning the whole document.
        if (start == 0 && end >= docLength) {
            return;
        }

        long key = key(start, end);
        PulseItem existing = active.get(key);
        found.put(key, existing != null ? existing : new PulseItem(start, end, now, info.getSeverity().myVal));
    }

    @Nullable
    private RangeHighlighterEx createHighlighter(PulseItem item, PulsingSettings.State settings) {
        HighlighterTargetArea targetArea = settings.wholeLine
                ? HighlighterTargetArea.LINES_IN_RANGE
                : HighlighterTargetArea.EXACT_RANGE;

        TextAttributes attributes = attributes(colorFor(settings, item.severityValue), Math.max(8, settings.maxAlpha / 3));
        RangeHighlighter created = editor.getMarkupModel().addRangeHighlighter(
                item.start, item.end, LAYER, attributes, targetArea);

        if (created instanceof RangeHighlighterEx ex) {
            return ex;
        }

        // Should not happen with the standard MarkupModel implementation.
        editor.getMarkupModel().removeHighlighter(created);
        return null;
    }

    /**
     * Accepts real errors, warnings and weak warnings (the last two only if enabled)
     * and rejects everything else.
     *
     * <p>Exact values are used because the platform also creates highlighters with
     * severities higher than ERROR (e.g. {@code ELEMENT_UNDER_CARET} = ERROR + 1 for
     * identifier usages, brace matching, etc.). Those must never pulse.</p>
     */
    private static boolean matches(PulsingSettings.State settings, HighlightSeverity severity) {
        int value = severity.myVal;
        if (value == ERROR_VALUE) {
            return true;
        }
        if (value == WARNING_VALUE) {
            return settings.includeWarnings;
        }
        if (value == WEAK_WARNING_VALUE) {
            return settings.includeWeakWarnings;
        }
        return false;
    }

    private static int colorFor(PulsingSettings.State settings, int severityValue) {
        if (severityValue == WARNING_VALUE) {
            return settings.warningColorRgb;
        }
        if (severityValue == WEAK_WARNING_VALUE) {
            return settings.weakWarningColorRgb;
        }
        return settings.colorRgb;
    }

    /** Paints a frame around the editor in the color of the worst problem in the file. */
    private void updateBorder(PulsingSettings.State settings) {
        if (borderFailed) {
            return;
        }

        JScrollPane scrollPane = editor instanceof EditorEx ex ? ex.getScrollPane() : null;
        if (scrollPane == null) {
            return;
        }

        if (!settings.enabled || !settings.borderEnabled) {
            restoreBorder(scrollPane);
            return;
        }

        int width = Math.max(1, Math.min(8, settings.borderWidth));
        int worst = worstSeverityValue();
        int rgb = worst < 0 ? -1 : colorFor(settings, worst);

        if (borderInstalled && installedBorderWidth == width && installedBorderRgb == rgb) {
            return;
        }

        if (!borderInstalled) {
            originalBorder = scrollPane.getBorder();
            borderInstalled = true;
        }

        Color color = rgb < 0 ? new Color(0, 0, 0, 0) : new Color(rgb);
        try {
            // NOTE: JViewport.setBorder() is deliberately unsupported by Swing,
            // so the frame is installed on the scroll pane itself.
            scrollPane.setBorder(BorderFactory.createLineBorder(color, width));
        } catch (RuntimeException e) {
            borderFailed = true;
            LOG.debug("Error Pulse: cannot install editor frame", e);
            restoreBorder(scrollPane);
            return;
        }
        installedBorderWidth = width;
        installedBorderRgb = rgb;
    }

    private int worstSeverityValue() {
        int worst = -1;
        for (PulseItem item : active.values()) {
            if (item.severityValue > worst) {
                worst = item.severityValue;
            }
        }
        return worst;
    }

    private void restoreBorder(JScrollPane scrollPane) {
        if (!borderInstalled) {
            return;
        }
        try {
            scrollPane.setBorder(originalBorder);
        } catch (RuntimeException ignored) {
            // Editor is being disposed, nothing to restore.
        }
        originalBorder = null;
        borderInstalled = false;
        installedBorderWidth = -1;
        installedBorderRgb = Integer.MIN_VALUE;
    }

    /**
     * Animation shape for a single problem.
     *
     * <p>The flash and the breathing pulse are two <b>sequential</b> phases, never mixed:
     * during {@link #FLASH_MS} only the decaying flash is shown, afterwards the breathing
     * starts from its dimmest point. Mixing two sine waves with different frequencies
     * produced the irregular flicker right after a problem appeared.</p>
     */
    private static int alphaFor(PulsingSettings.State settings, PulseItem item, long now) {
        long age = now - item.birth;
        double intensity;

        if (settings.flashOnNewError && age >= 0 && age < FLASH_MS) {
            double decay = 1.0 - age / (double) FLASH_MS;
            double pulse = 0.5 + 0.5 * Math.cos(2 * Math.PI * age / FLASH_PULSE_MS);
            intensity = (0.25 + 0.75 * pulse) * decay;
        }
        else {
            long period = Math.max(300, settings.periodMs);
            long breatheStart = settings.flashOnNewError ? item.birth + FLASH_MS : item.birth;
            long time = Math.max(0, now - breatheStart);
            double phase = (time % period) / (double) period;
            intensity = 0.5 - 0.5 * Math.cos(2 * Math.PI * phase);
        }

        intensity = Math.max(0.0, Math.min(1.0, intensity));
        int alpha = (int) Math.round(settings.maxAlpha * (0.30 + 0.70 * intensity));
        return Math.max(4, Math.min(255, alpha));
    }

    private static TextAttributes attributes(int rgb, int alpha) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        TextAttributes attributes = new TextAttributes();
        attributes.setBackgroundColor(new Color(red, green, blue, Math.max(0, Math.min(255, alpha))));
        return attributes;
    }

    private static long key(int start, int end) {
        return ((long) start << 32) | (end & 0xFFFFFFFFL);
    }

    private void removeHighlighter(PulseItem item) {
        RangeHighlighterEx highlighter = item.highlighter;
        item.highlighter = null;
        if (highlighter != null && highlighter.isValid()) {
            editor.getMarkupModel().removeHighlighter(highlighter);
        }
    }

    private void removeAll() {
        for (PulseItem item : new ArrayList<>(active.values())) {
            removeHighlighter(item);
        }
        active.clear();
    }

    /** Re-creates highlighters on the next tick, e.g. after the settings changed. */
    public void reloadStyle() {
        removeAll();
        lastScan = 0L;
    }

    @Override
    public void dispose() {
        timer.stop();
        if (!editor.isDisposed()) {
            JScrollPane scrollPane = editor instanceof EditorEx ex ? ex.getScrollPane() : null;
            if (scrollPane != null) {
                restoreBorder(scrollPane);
            }
            removeAll();
        } else {
            active.clear();
            borderInstalled = false;
            originalBorder = null;
        }
    }
}

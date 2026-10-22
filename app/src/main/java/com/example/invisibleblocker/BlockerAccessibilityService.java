package com.example.invisibleblocker;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BlockerAccessibilityService extends AccessibilityService {

    private static final String INSTAGRAM = "com.instagram.android";
    private static final String YOUTUBE = "com.google.android.youtube";
    private static final String FACEBOOK = "com.facebook.katana";

    private static final long QUICK_GAP_MAX = 800L;
    private static final long PAUSE_MIN = 900L;
    private static final long PAUSE_MAX = 3000L;
    private static final long DUPLICATE_EVENT_MS = 80L;
    private static final long UNLOCK_DURATION = 10L * 60L * 1000L;
    private static final long BACK_ACTION_GAP = 180L;

    private final Map<String, Long> unlockedUntil = new HashMap<>();
    private final List<Long> tapTimes = new ArrayList<>();

    private String patternPackage = "";
    private String foregroundPackage = "";
    private long lastBackActionAt = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (event == null || event.getPackageName() == null) {
            return;
        }

        String packageName = event.getPackageName().toString();
        int eventType = event.getEventType();

        /*
         * Reset temporary unlock after leaving or minimising
         * Instagram, YouTube, or Facebook.
         */
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleForegroundChange(packageName);
        }

        if (!isSupportedPackage(packageName)) {
            return;
        }

        if (isUnlocked(packageName)) {
            return;
        }

        boolean clickEvent =
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED;

        boolean selectedEvent =
                eventType == AccessibilityEvent.TYPE_VIEW_SELECTED;

        if (!clickEvent && !selectedEvent) {
            return;
        }

        String combinedLabel = getCombinedEventLabel(event);
        String viewId = getSourceViewId(event);

        if (!isBlockedNavigationControl(
                packageName,
                combinedLabel,
                viewId)) {
            return;
        }

        long now = SystemClock.elapsedRealtime();

        /*
         * Count only CLICKED events. One physical tap can emit
         * both CLICKED and SELECTED events.
         */
        if (clickEvent && registerPatternTap(packageName, now)) {
            unlockPackage(packageName);
            return;
        }

        blockTabNavigation();
    }

    // =========================================================
    // SUPPORTED APPS
    // =========================================================

    private boolean isSupportedPackage(String packageName) {

        return INSTAGRAM.equals(packageName)
                || YOUTUBE.equals(packageName)
                || FACEBOOK.equals(packageName);
    }

    // =========================================================
    // FOREGROUND RESET
    // =========================================================

    private void handleForegroundChange(String newPackage) {

        if (newPackage == null || newPackage.isEmpty()) {
            return;
        }

        if (!foregroundPackage.isEmpty()
                && isSupportedPackage(foregroundPackage)
                && !foregroundPackage.equals(newPackage)) {

            resetPackageState(foregroundPackage);
        }

        foregroundPackage = newPackage;
    }

    private void resetPackageState(String packageName) {

        unlockedUntil.remove(packageName);

        if (packageName.equals(patternPackage)) {
            tapTimes.clear();
            patternPackage = "";
        }

        lastBackActionAt = 0L;
    }

    // =========================================================
    // TAP PATTERN
    // =========================================================

    private boolean registerPatternTap(
            String packageName,
            long now) {

        if (!packageName.equals(patternPackage)) {
            tapTimes.clear();
            patternPackage = packageName;
        }

        if (!tapTimes.isEmpty()) {

            long previousTap =
                    tapTimes.get(tapTimes.size() - 1);

            long gap = now - previousTap;

            if (gap < DUPLICATE_EVENT_MS) {
                return false;
            }

            if (gap > PAUSE_MAX) {
                tapTimes.clear();
            }
        }

        tapTimes.add(now);

        while (tapTimes.size() > 4) {
            tapTimes.remove(0);
        }

        if (tapTimes.size() < 4) {
            return false;
        }

        long firstPairGap =
                tapTimes.get(1) - tapTimes.get(0);

        long pauseGap =
                tapTimes.get(2) - tapTimes.get(1);

        long secondPairGap =
                tapTimes.get(3) - tapTimes.get(2);

        boolean firstPairValid =
                firstPairGap <= QUICK_GAP_MAX;

        boolean pauseValid =
                pauseGap >= PAUSE_MIN
                        && pauseGap <= PAUSE_MAX;

        boolean secondPairValid =
                secondPairGap <= QUICK_GAP_MAX;

        if (firstPairValid
                && pauseValid
                && secondPairValid) {

            tapTimes.clear();
            patternPackage = "";

            return true;
        }

        return false;
    }

    // =========================================================
    // UNLOCK
    // =========================================================

    private void unlockPackage(String packageName) {

        long expiry =
                SystemClock.elapsedRealtime()
                        + UNLOCK_DURATION;

        unlockedUntil.put(packageName, expiry);

        tapTimes.clear();
        patternPackage = "";
        lastBackActionAt = 0L;

        Toast.makeText(
                this,
                getAppName(packageName)
                        + " unlocked for 10 minutes",
                Toast.LENGTH_SHORT
        ).show();
    }

    private boolean isUnlocked(String packageName) {

        Long expiry = unlockedUntil.get(packageName);

        if (expiry == null) {
            return false;
        }

        if (SystemClock.elapsedRealtime() < expiry) {
            return true;
        }

        resetPackageState(packageName);
        return false;
    }

    // =========================================================
    // LABEL AND VIEW-ID DETECTION
    // =========================================================

    private String getCombinedEventLabel(AccessibilityEvent event) {

        StringBuilder result = new StringBuilder();

        if (event.getText() != null) {
            for (CharSequence value : event.getText()) {
                appendValue(result, value);
            }
        }

        appendValue(result, event.getContentDescription());

        AccessibilityNodeInfo source = event.getSource();

        /*
         * Read the clicked node and up to four parent nodes.
         * YouTube and Facebook often place the tab description
         * on a parent container instead of the clicked child.
         */
        int level = 0;

        while (source != null && level < 5) {

            appendValue(result, source.getText());
            appendValue(result, source.getContentDescription());
            appendValue(result, source.getViewIdResourceName());

            AccessibilityNodeInfo parent = source.getParent();

            if (level > 0) {
                source.recycle();
            }

            source = parent;
            level++;
        }

        if (source != null) {
            source.recycle();
        }

        return normalize(result.toString());
    }

    private String getSourceViewId(AccessibilityEvent event) {

        AccessibilityNodeInfo source = event.getSource();

        if (source == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int level = 0;

        while (source != null && level < 5) {

            String currentId =
                    source.getViewIdResourceName();

            if (currentId != null) {
                result.append(currentId);
                result.append(" ");
            }

            AccessibilityNodeInfo parent =
                    source.getParent();

            if (level > 0) {
                source.recycle();
            }

            source = parent;
            level++;
        }

        if (source != null) {
            source.recycle();
        }

        return normalize(result.toString());
    }

    private void appendValue(
            StringBuilder builder,
            CharSequence value) {

        if (value == null) {
            return;
        }

        String text = value.toString().trim();

        if (!text.isEmpty()) {
            builder.append(text);
            builder.append(" ");
        }
    }

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private boolean isBlockedNavigationControl(
            String packageName,
            String label,
            String viewId) {

        if (YOUTUBE.equals(packageName)) {

            boolean shortsLabel =
                    containsWord(label, "shorts")
                            || label.contains("youtube shorts");

            boolean shortsNavigationId =
                    viewId.contains("pivot")
                            || viewId.contains("bottom")
                            || viewId.contains("navigation")
                            || viewId.contains("shorts")
                            || viewId.contains("reel");

            return shortsLabel && shortsNavigationId;
        }

        if (INSTAGRAM.equals(packageName)) {

            boolean reelsLabel =
                    containsWord(label, "reels")
                            || label.contains("instagram reels");

            boolean reelsNavigationId =
                    viewId.contains("tab")
                            || viewId.contains("navigation")
                            || viewId.contains("clips")
                            || viewId.contains("reels");

            /*
             * Instagram sometimes exposes only the word Reels
             * without a useful view ID.
             */
            return reelsLabel
                    && (reelsNavigationId || viewId.isEmpty());
        }

        if (FACEBOOK.equals(packageName)) {

            boolean reelsLabel =
                    containsWord(label, "reels")
                            || label.contains("facebook reels")
                            || label.contains("watch reels");

            boolean reelsNavigationId =
                    viewId.contains("tab")
                            || viewId.contains("navigation")
                            || viewId.contains("reels")
                            || viewId.contains("watch")
                            || viewId.contains("video");

            return reelsLabel
                    && (reelsNavigationId || viewId.isEmpty());
        }

        return false;
    }

    private boolean containsWord(
            String text,
            String word) {

        if (text == null || text.isEmpty()) {
            return false;
        }

        String paddedText =
                " " + text + " ";

        String paddedWord =
                " " + word + " ";

        return paddedText.contains(paddedWord)
                || text.startsWith(word + ",")
                || text.startsWith(word + " tab")
                || text.endsWith(" " + word)
                || text.contains(word + " selected")
                || text.contains(word + " button");
    }

    // =========================================================
    // BLOCK ACTION
    // =========================================================

    private void blockTabNavigation() {

        long now = SystemClock.elapsedRealtime();

        if (now - lastBackActionAt < BACK_ACTION_GAP) {
            return;
        }

        lastBackActionAt = now;

        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    // =========================================================
    // APP NAMES
    // =========================================================

    private String getAppName(String packageName) {

        if (INSTAGRAM.equals(packageName)) {
            return "Instagram Reels";
        }

        if (YOUTUBE.equals(packageName)) {
            return "YouTube Shorts";
        }

        if (FACEBOOK.equals(packageName)) {
            return "Facebook Reels";
        }

        return "Short videos";
    }

    // =========================================================
    // SERVICE CALLBACKS
    // =========================================================

    @Override
    public void onInterrupt() {
        // Required callback.
    }

    @Override
    public void onDestroy() {

        unlockedUntil.clear();
        tapTimes.clear();

        super.onDestroy();
    }
}

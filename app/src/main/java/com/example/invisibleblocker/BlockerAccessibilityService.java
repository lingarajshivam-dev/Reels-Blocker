package com.example.invisibleblocker;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BlockerAccessibilityService extends AccessibilityService {

    private static final String INSTAGRAM =
            "com.instagram.android";

    private static final String YOUTUBE =
            "com.google.android.youtube";

    private static final String FACEBOOK =
            "com.facebook.katana";

    /*
     * Unlock pattern:
     * quick tap-tap, pause, quick tap-tap
     */
    private static final long QUICK_GAP_MAX = 700L;
    private static final long PAUSE_MIN = 1000L;
    private static final long PAUSE_MAX = 3000L;
    private static final long DUPLICATE_EVENT_MS = 80L;

    /*
     * Temporary unlock duration: 10 minutes.
     */
    private static final long UNLOCK_DURATION =
            10L * 60L * 1000L;

    /*
     * Duplicate Back actions occurring within this period
     * are ignored.
     */
    private static final long BACK_ACTION_GAP = 180L;

    /*
     * Each supported app gets an independent unlock timer.
     */
    private final Map<String, Long> unlockedUntil =
            new HashMap<>();

    /*
     * Stores the four taps used for the secret pattern.
     */
    private final List<Long> tapTimes =
            new ArrayList<>();

    private String patternPackage = "";
    private String foregroundPackage = "";

    private long lastBackActionAt = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (event == null || event.getPackageName() == null) {
            return;
        }

        String packageName =
                event.getPackageName().toString();

        int eventType =
                event.getEventType();

        /*
         * This check runs for all apps.
         *
         * When Instagram, YouTube, or Facebook is minimised,
         * closed, or replaced by another app, its 10-minute
         * unlock is immediately removed.
         */
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleForegroundChange(packageName);
        }

        /*
         * Ignore clicks from apps other than the three
         * supported social apps.
         */
        if (!isSupportedPackage(packageName)) {
            return;
        }

        /*
         * If this specific app is temporarily unlocked,
         * allow its Reels or Shorts tab.
         */
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

        String label =
                getEventLabel(event);

        /*
         * Only block taps on the Reels or Shorts navigation tab.
         *
         * Reels or Shorts opened from DMs, Messenger,
         * notifications, WhatsApp, email, or direct links are
         * allowed because those actions do not tap the app's
         * Reels or Shorts navigation tab.
         */
        if (!isBlockedNavigationLabel(packageName, label)) {
            return;
        }

        long now =
                SystemClock.elapsedRealtime();

        /*
         * Only CLICKED events count toward the secret pattern.
         * Some apps emit both CLICKED and SELECTED for one tap.
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
    // FOREGROUND AND MINIMISE RESET
    // =========================================================

    private void handleForegroundChange(String newPackage) {

        if (newPackage == null || newPackage.isEmpty()) {
            return;
        }

        /*
         * When the user leaves one of the supported apps,
         * remove only that app's unlock and unfinished pattern.
         */
        if (!foregroundPackage.isEmpty()
                && isSupportedPackage(foregroundPackage)
                && !foregroundPackage.equals(newPackage)) {

            resetPackageState(foregroundPackage);
        }

        foregroundPackage =
                newPackage;
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
    // TAP-TAP, PAUSE, TAP-TAP PATTERN
    // =========================================================

    private boolean registerPatternTap(
            String packageName,
            long now) {

        /*
         * A pattern cannot continue across different apps.
         */
        if (!packageName.equals(patternPackage)) {

            tapTimes.clear();
            patternPackage = packageName;
        }

        if (!tapTimes.isEmpty()) {

            long previousTap =
                    tapTimes.get(tapTimes.size() - 1);

            long gap =
                    now - previousTap;

            /*
             * Ignore duplicate Accessibility events generated
             * from one physical tap.
             */
            if (gap < DUPLICATE_EVENT_MS) {
                return false;
            }

            /*
             * If the delay is longer than the allowed pause,
             * start a new pattern.
             */
            if (gap > PAUSE_MAX) {
                tapTimes.clear();
            }
        }

        tapTimes.add(now);

        /*
         * Only the latest four valid taps are required.
         */
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
    // TEMPORARY UNLOCK
    // =========================================================

    private void unlockPackage(String packageName) {

        long expiry =
                SystemClock.elapsedRealtime()
                        + UNLOCK_DURATION;

        unlockedUntil.put(
                packageName,
                expiry
        );

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

        Long expiry =
                unlockedUntil.get(packageName);

        if (expiry == null) {
            return false;
        }

        if (SystemClock.elapsedRealtime() < expiry) {
            return true;
        }

        /*
         * Ten minutes have expired.
         */
        resetPackageState(packageName);

        return false;
    }

    // =========================================================
    // EVENT LABEL DETECTION
    // =========================================================

    private String getEventLabel(AccessibilityEvent event) {

        StringBuilder label =
                new StringBuilder();

        if (event.getText() != null) {

            for (CharSequence value : event.getText()) {

                if (value != null) {
                    label.append(value);
                    label.append(" ");
                }
            }
        }

        if (event.getContentDescription() != null) {

            label.append(
                    event.getContentDescription()
            );
        }

        return label.toString()
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private boolean isBlockedNavigationLabel(
            String packageName,
            String label) {

        if (label == null || label.isEmpty()) {
            return false;
        }

        if (YOUTUBE.equals(packageName)) {

            return label.equals("shorts")
                    || label.equals("youtube shorts")
                    || label.contains("shorts tab");
        }

        if (INSTAGRAM.equals(packageName)) {

            return label.equals("reels")
                    || label.equals("instagram reels")
                    || label.contains("reels tab");
        }

        if (FACEBOOK.equals(packageName)) {

            return label.equals("reels")
                    || label.equals("facebook reels")
                    || label.contains("reels tab")
                    || label.contains("watch reels");
        }

        return false;
    }

    // =========================================================
    // BLOCK ACTION
    // =========================================================

    private void blockTabNavigation() {

        long now =
                SystemClock.elapsedRealtime();

        /*
         * Prevent multiple Back actions from duplicate events.
         */
        if (now - lastBackActionAt < BACK_ACTION_GAP) {
            return;
        }

        lastBackActionAt =
                now;

        performGlobalAction(
                GLOBAL_ACTION_BACK
        );
    }

    // =========================================================
    // DISPLAY NAME
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
        /*
         * Required AccessibilityService callback.
         */
    }

    @Override
    public void onDestroy() {

        unlockedUntil.clear();
        tapTimes.clear();

        super.onDestroy();
    }
}

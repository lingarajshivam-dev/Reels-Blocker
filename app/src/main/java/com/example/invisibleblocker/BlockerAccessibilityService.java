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
     * tap-tap, pause, tap-tap
     */
    private static final long QUICK_GAP_MAX = 700L;
    private static final long PAUSE_MIN = 1000L;
    private static final long PAUSE_MAX = 3000L;
    private static final long DUPLICATE_EVENT_MS = 80L;

    private static final long UNLOCK_DURATION =
            10L * 60L * 1000L;

    /*
     * Separate unlock expiry for each supported application.
     */
    private final Map<String, Long> unlockedUntil =
            new HashMap<>();

    /*
     * Tap-pattern tracking.
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
         * Process all window changes, including launcher and
         * unsupported apps. This immediately resets an unlock
         * when Instagram, YouTube, or Facebook is left.
         */
        if (eventType
                == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            handleForegroundChange(packageName);
        }

        if (!isSupportedPackage(packageName)) {
            return;
        }

        /*
         * While this specific app is unlocked, allow its tab
         * and its Reels/Shorts player.
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
         * DM and direct-link players are not blocked here.
         * Only the navigation control labelled Reels/Shorts
         * activates the blocker and secret pattern.
         */
        if (!isBlockedNavigationLabel(packageName, label)) {
            return;
        }

        long now =
                SystemClock.elapsedRealtime();

        /*
         * Count only click events. Some applications emit
         * CLICKED and SELECTED for one physical tap.
         */
        if (clickEvent
         *      && registerPatternTap(packag*Name, now)) {

            unlockP*ckage(packageName);
            re*urn;
        }

        blockTabNa*igation();
    }

    private boolean isSupportedPackage(String packageName) {
        return INSTAGRAM.equals(packageName)
                || YOUTUBE.equals(packageName)
                || FACEBOOK.equals(packageName);
    }

    // ================= FOREGROUND RESET =================

    private void handleForegroundChange(String newPackage) {
        if (newPackage == null || newPackage.isEmpty()) {
            return;
        }

        /*
         * Leaving a supported social app immediately clears
         * only that application's unlock.
         */
        if (!foregroundPac*age.isEmpty()
                && i*SupportedPackage(foregroundPackage*
                && !foregroundPac*age.equals(newPackage)) {

       *    resetPackageState(foregroundPa*kage);
        }

        foregrou*dPackage =
                newPack*ge;
    }

    private void resetP*ckageState(String packageName) {
 *      unlockedUntil.remove(package*ame);

        if (packageName.equ*ls(patternPackage)) {
            *apTimes.clear();
            patte*nPackage = "";
        }

        *astBackActionAt = 0L;
    }

    // ================= PATTERN =================

    private boolean registerPatternTap(
            String packageName,
            long now) {

        if (!packageName.equals(patternPackage)) {
            tapTimes.clear();
            patternPackage = packageName;
        }

        if (!tapTimes.isEmpty()) {
            long lastTap =
                    tapTimes.get(tapTimes.size() - 1);

            long gap =
                    now - lastTap;

            /*
             * Ignore duplicate accessibility events coming
             * from one physical tap.
             */
            if (gap < DUPLICATE_EVENT_MS) {
                return false;
            }

            /*
             * An excessively long delay starts a new pattern.
             */
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

    // ================= UNLOCK =================

    private void unlockPackage(String packageName) {
        long expiry =
                SystemClock.elapsedRealtime()
                        + UNLOCK_DURATION;

        unlockedUntil.put(packageName, expiry);

        tapTimes.clear();
        patternPackage = "";

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

        resetPackageState(packageName);
        return false;
    }

    // ================= LABEL DETECTION =================

    private String getEventLabel(
            AccessibilityEvent event) {

        StringBuilder label =
                new StringBuilder();

        if (event.getText() != null) {
            for (CharSequence value : event.getText()) {
                if (value != null) {
                    label.append(value).append(" ");
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

        if (label.isEmpty()) {
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

    // ================= BLOCK ACTION =================

    private void blockTabNavigation() {
        long now =
                SystemClock.elapsedRealtime();

        /*
         * Prevent duplicate Back actions caused by multiple
         * accessibility events for one physical tap.
         */
        if (now - lastBackActionAt < 180L) {
            return;
        }

        lastBackActionAt = now;

        performGlobalAction(
                GLOBAL_ACTION_BACK
        );
    }

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

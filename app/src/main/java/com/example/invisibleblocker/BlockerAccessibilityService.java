package com.example.invisibleblocker;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
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
    private static final long BACK_ACTION_GAP = 250L;

    /*
     * External links normally open the supported app shortly after the
     * foreground package changes. This window allows the first externally
     * opened Reel or Short.
     */
    private static final long EXTERNAL_ENTRY_WINDOW = 6000L;

    /*
     * How long to remember that the user was inside an Instagram DM.
     */
    private static final long DM_ENTRY_WINDOW = 5000L;

    private static final String[] YOUTUBE_SHORT_IDS = {
            YOUTUBE + ":id/reel_watch_fragment_root",
            YOUTUBE + ":id/reel_recycler",
            YOUTUBE + ":id/reel_player_page_container",
            YOUTUBE + ":id/reel_watch_player",
            YOUTUBE + ":id/shorts_player",
            YOUTUBE + ":id/shorts_container"
    };

    private static final String[] INSTAGRAM_REEL_IDS = {
            INSTAGRAM + ":id/clips_viewer_view_pager",
            INSTAGRAM + ":id/clips_viewer_container",
            INSTAGRAM + ":id/root_clips_layout",
            INSTAGRAM + ":id/clips_video_container",
            INSTAGRAM + ":id/reel_viewer_container",
            INSTAGRAM + ":id/reel_viewer_root"
    };

    private static final String[] FACEBOOK_REEL_IDS = {
            FACEBOOK + ":id/reels_viewer",
            FACEBOOK + ":id/reels_viewer_container",
            FACEBOOK + ":id/reels_player",
            FACEBOOK + ":id/reels_player_container",
            FACEBOOK + ":id/reels_video_container",
            FACEBOOK + ":id/reel_viewer_root",
            FACEBOOK + ":id/reel_video_container",
            FACEBOOK + ":id/watch_reels_container"
    };

    private static final String[] INSTAGRAM_DM_IDS = {
            INSTAGRAM + ":id/row_thread_composer_edittext",
            INSTAGRAM + ":id/direct_thread_container",
            INSTAGRAM + ":id/direct_thread_header",
            INSTAGRAM + ":id/thread_title",
            INSTAGRAM + ":id/direct_thread_recycler_view"
    };

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Map<String, Long> unlockedUntil =
            new HashMap<>();

    private final List<Long> tapTimes =
            new ArrayList<>();

    private String foregroundPackage = "";
    private String patternPackage = "";

    private long lastBackActionAt = 0L;
    private long externalEntryUntil = 0L;
    private long dmEntryUntil = 0L;

    private String externalEntryPackage = "";

    /*
     * Becomes true after a valid DM or external-link player is allowed.
     * Cleared when the relevant social app is left.
     */
    private boolean allowedExternalPlayer = false;

    private final Runnable screenCheck =
            this::checkCurrentScreen;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

        if (event == null || event.getPackageName() == null) {
            return;
        }

        String packageName =
                event.getPackageName().toString();

        int eventType =
                event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleForegroundChange(packageName);
        }

        if (!isSupportedPackage(packageName)) {
            return;
        }

        /*
         * Temporarily unlocked application.
         */
        if (isUnlocked(packageName)) {
            return;
        }

        /*
         * Remember when the user is visibly inside an Instagram DM.
         */
        if (INSTAGRAM.equals(packageName)) {

            AccessibilityNodeInfo root =
                    getRootInActiveWindow();

            if (root != null
                    && containsVisibleId(root, INSTAGRAM_DM_IDS)) {

                dmEntryUntil =
                        SystemClock.elapsedRealtime()
                                + DM_ENTRY_WINDOW;
            }
        }

        boolean clickEvent =
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED;

        boolean selectedEvent =
                eventType == AccessibilityEvent.TYPE_VIEW_SELECTED;

        if (clickEvent || selectedEvent) {

            String label =
                    getCombinedEventLabel(event);

            String viewIds =
                    getSourceViewIds(event);

            if (isBlockedNavigationControl(
                    packageName,
                    label,
                    viewIds)) {

                /*
                 * A direct tab click is never treated as an external
                 * or DM entry.
                 */
                clearAllowedEntry();

                long now =
                        SystemClock.elapsedRealtime();

                if (clickEvent
                        && registerPatternTap(packageName, now)) {

                    unlockPackage(packageName);
                    return;
                }

                blockNavigation();
                scheduleScreenChecks();
                return;
            }
        }

        /*
         * Check the actual player after screen, content, click,
         * selection, or scroll events.
         */
        scheduleScreenChecks();
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
    // FOREGROUND TRACKING
    // =========================================================

    private void handleForegroundChange(String newPackage) {

        if (newPackage == null || newPackage.isEmpty()) {
            return;
        }

        String oldPackage =
                foregroundPackage;

        /*
         * Leaving a supported app immediately resets its timer,
         * secret pattern, and allowed-entry state.
         */
        if (!oldPackage.isEmpty()
                && isSupportedPackage(oldPackage)
                && !oldPackage.equals(newPackage)) {

            resetPackageState(oldPackage);
            clearAllowedEntry();
        }

        /*
         * Entering Instagram, YouTube, or Facebook from another
         * package can indicate a direct link, notification,
         * Messenger link, WhatsApp link, browser link, or email link.
         */
        if (!isSupportedPackage(oldPackage)
                && isSupportedPackage(newPackage)) {

            externalEntryPackage =
                    newPackage;

            externalEntryUntil =
                    SystemClock.elapsedRealtime()
                            + EXTERNAL_ENTRY_WINDOW;
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

    private void clearAllowedEntry() {

        externalEntryPackage = "";
        externalEntryUntil = 0L;
        dmEntryUntil = 0L;
        allowedExternalPlayer = false;
    }

    private boolean isExternalEntryActive(String packageName) {

        return packageName.equals(externalEntryPackage)
                && SystemClock.elapsedRealtime()
                <= externalEntryUntil;
    }

    private boolean isDmEntryActive() {

        return SystemClock.elapsedRealtime()
                <= dmEntryUntil;
    }

    // =========================================================
    // PLAYER DETECTION
    // =========================================================

    private void scheduleScreenChecks() {

        handler.removeCallbacks(screenCheck);

        /*
         * Immediate and delayed checks handle asynchronously
         * rendered Shorts and Reels screens.
         */
        handler.postDelayed(screenCheck, 100L);
        handler.postDelayed(screenCheck, 350L);
        handler.postDelayed(screenCheck, 750L);
    }

    private void checkCurrentScreen() {

        AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null || root.getPackageName() == null) {
            return;
        }

        String packageName =
                root.getPackageName().toString();

        if (!isSupportedPackage(packageName)
                || isUnlocked(packageName)) {

            return;
        }

        boolean playerVisible =
                isBlockedPlayerVisible(root, packageName);

        if (!playerVisible) {
            return;
        }

        /*
         * Direct external link or Messenger/WhatsApp/browser entry.
         */
        if (isExternalEntryActive(packageName)) {

            allowedExternalPlayer = true;
            externalEntryUntil = 0L;
            externalEntryPackage = "";

            return;
        }

        /*
         * Instagram Reel opened from a recently visible DM.
         */
        if (INSTAGRAM.equals(packageName)
                && isDmEntryActive()) {

            allowedExternalPlayer = true;
            dmEntryUntil = 0L;

            return;
        }

        /*
         * Continue allowing the external/DM player until the social
         * app is left. Direct tab taps clear this before detection.
         */
        if (allowedExternalPlayer) {
            return;
        }

        /*
         * A player opened inside YouTube Home, Facebook Home,
         * search, profile, group, or dedicated Reels/Shorts tab
         * is blocked.
         */
        blockNavigation();
    }

    private boolean isBlockedPlayerVisible(
            AccessibilityNodeInfo root,
            String packageName) {

        if (YOUTUBE.equals(packageName)) {

            if (containsVisibleId(root, YOUTUBE_SHORT_IDS)) {
                return true;
            }

            return hasPlayerSignals(
                    root,
                    "shorts"
            );
        }

        if (INSTAGRAM.equals(packageName)) {

            if (containsVisibleId(root, INSTAGRAM_REEL_IDS)) {
                return true;
            }

            return hasPlayerSignals(
                    root,
                    "reels"
            );
        }

        if (FACEBOOK.equals(packageName)) {

            if (containsVisibleId(root, FACEBOOK_REEL_IDS)) {
                return true;
            }

            return hasPlayerSignals(
                    root,
                    "reels"
            );
        }

        return false;
    }

    private boolean containsVisibleId(
            AccessibilityNodeInfo root,
            String[] ids) {

        for (String id : ids) {

            List<AccessibilityNodeInfo> nodes =
                    root.findAccessibilityNodeInfosByViewId(id);

            if (nodes == null) {
                continue;
            }

            for (AccessibilityNodeInfo node : nodes) {

                if (node != null
                        && node.isVisibleToUser()) {

                    return true;
                }
            }
        }

        return false;
    }

    /*
     * Fallback detection for application versions that do not
     * expose stable view IDs.
     */
    private boolean hasPlayerSignals(
            AccessibilityNodeInfo root,
            String playerType) {

        PlayerSignals signals =
                new PlayerSignals();

        scanNodeTree(
                root,
                signals,
                0
        );

        boolean correctPlayerLabel;

        if ("shorts".equals(playerType)) {

            correctPlayerLabel =
                    signals.shorts;
        } else {

            correctPlayerLabel =
                    signals.reels;
        }

        int actionCount = 0;

        if (signals.like) {
            actionCount++;
        }

        if (signals.comment) {
            actionCount++;
        }

        if (signals.share) {
            actionCount++;
        }

        if (signals.remix) {
            actionCount++;
        }

        /*
         * Require the player label and at least two typical
         * full-screen short-video controls.
         */
        return correctPlayerLabel
                && actionCount >= 2;
    }

    private void scanNodeTree(
            AccessibilityNodeInfo node,
            PlayerSignals signals,
            int depth) {

        if (node == null
                || depth > 35
                || signals.isComplete()) {

            return;
        }

        String text =
                normalize(
                        valueOf(node.getText())
                                + " "
                                + valueOf(node.getContentDescription())
                                + " "
                                + valueOf(node.getViewIdResourceName())
                );

        if (!text.isEmpty()) {

            if (text.contains("shorts")
                    || text.contains("reel_watch")
                    || text.contains("reel_recycler")) {

                signals.shorts = true;
            }

            if (text.contains("reels")
                    || text.contains("reel viewer")
                    || text.contains("reels_viewer")) {

                signals.reels = true;
            }

            if (containsActionWord(text, "like")) {
                signals.like = true;
            }

            if (containsActionWord(text, "comment")) {
                signals.comment = true;
            }

            if (containsActionWord(text, "share")) {
                signals.share = true;
            }

            if (containsActionWord(text, "remix")) {
                signals.remix = true;
            }
        }

        int childCount =
                node.getChildCount();

        for (int index = 0;
             index < childCount;
             index++) {

            AccessibilityNodeInfo child =
                    node.getChild(index);

            if (child != null) {

                scanNodeTree(
                        child,
                        signals,
                        depth + 1
                );

                child.recycle();
            }
        }
    }

    private boolean containsActionWord(
            String text,
            String word) {

        return text.equals(word)
                || text.startsWith(word + " ")
                || text.startsWith(word + ",")
                || text.contains(" " + word + " ")
                || text.endsWith(" " + word);
    }

    private static class PlayerSignals {

        boolean shorts;
        boolean reels;
        boolean like;
        boolean comment;
        boolean share;
        boolean remix;

        boolean isComplete() {

            int actionCount = 0;

            if (like) {
                actionCount++;
            }

            if (comment) {
                actionCount++;
            }

            if (share) {
                actionCount++;
            }

            if (remix) {
                actionCount++;
            }

            return (shorts || reels)
                    && actionCount >= 3;
        }
    }

    // =========================================================
    // NAVIGATION CONTROL DETECTION
    // =========================================================

    private boolean isBlockedNavigationControl(
            String packageName,
            String label,
            String viewIds) {

        if (YOUTUBE.equals(packageName)) {

            boolean labelMatches =
                    containsWord(label, "shorts");

            boolean navigationMatches =
                    viewIds.contains("pivot")
                            || viewIds.contains("bottom")
                            || viewIds.contains("navigation")
                            || viewIds.contains("shorts");

            return labelMatches
                    && (navigationMatches
                    || label.contains("tab"));
        }

        if (INSTAGRAM.equals(packageName)) {

            boolean labelMatches =
                    containsWord(label, "reels");

            boolean navigationMatches =
                    viewIds.contains("tab")
                            || viewIds.contains("navigation")
                            || viewIds.contains("clips")
                            || viewIds.contains("reels");

            return labelMatches
                    && (navigationMatches
                    || label.contains("tab")
                    || viewIds.isEmpty());
        }

        if (FACEBOOK.equals(packageName)) {

            boolean labelMatches =
                    containsWord(label, "reels")
                            || label.contains("watch reels")
                            || label.contains("video reels");

            boolean navigationMatches =
                    viewIds.contains("tab")
                            || viewIds.contains("navigation")
                            || viewIds.contains("reels")
                            || viewIds.contains("watch")
                            || viewIds.contains("video")
                            || viewIds.contains("pivot");

            return labelMatches
                    && (navigationMatches
                    || label.contains("tab")
                    || label.contains("button")
                    || viewIds.isEmpty());
        }

        return false;
    }

    private String getCombinedEventLabel(
            AccessibilityEvent event) {

        StringBuilder builder =
                new StringBuilder();

        if (event.getText() != null) {

            for (CharSequence value : event.getText()) {
                appendValue(builder, value);
            }
        }

        appendValue(
                builder,
                event.getContentDescription()
        );

        AccessibilityNodeInfo current =
                event.getSource();

        int depth = 0;

        while (current != null && depth < 6) {

            appendValue(
                    builder,
                    current.getText()
            );

            appendValue(
                    builder,
                    current.getContentDescription()
            );

            appendValue(
                    builder,
                    current.getViewIdResourceName()
            );

            AccessibilityNodeInfo parent =
                    current.getParent();

            if (depth > 0) {
                current.recycle();
            }

            current = parent;
            depth++;
        }

        if (current != null) {
            current.recycle();
        }

        return normalize(
                builder.toString()
        );
    }

    private String getSourceViewIds(
            AccessibilityEvent event) {

        StringBuilder builder =
                new StringBuilder();

        AccessibilityNodeInfo current =
                event.getSource();

        int depth = 0;

        while (current != null && depth < 6) {

            appendValue(
                    builder,
                    current.getViewIdResourceName()
            );

            AccessibilityNodeInfo parent =
                    current.getParent();

            if (depth > 0) {
                current.recycle();
            }

            current = parent;
            depth++;
        }

        if (current != null) {
            current.recycle();
        }

        return normalize(
                builder.toString()
        );
    }

    private void appendValue(
            StringBuilder builder,
            CharSequence value) {

        if (value == null) {
            return;
        }

        String text =
                value.toString().trim();

        if (!text.isEmpty()) {

            builder.append(text);
            builder.append(" ");
        }
    }

    private String valueOf(
            CharSequence value) {

        return value == null
                ? ""
                : value.toString();
    }

    private String normalize(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private boolean containsWord(
            String text,
            String word) {

        if (text == null || text.isEmpty()) {
            return false;
        }

        return text.equals(word)
                || text.startsWith(word + " ")
                || text.startsWith(word + ",")
                || text.contains(" " + word + " ")
                || text.endsWith(" " + word);
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
                    tapTimes.get(
                            tapTimes.size() - 1
                    );

            long gap =
                    now - previousTap;

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
                tapTimes.get(1)
                        - tapTimes.get(0);

        long pauseGap =
                tapTimes.get(2)
                        - tapTimes.get(1);

        long secondPairGap =
                tapTimes.get(3)
                        - tapTimes.get(2);

        boolean matched =
                firstPairGap <= QUICK_GAP_MAX
                        && pauseGap >= PAUSE_MIN
                        && pauseGap <= PAUSE_MAX
                        && secondPairGap <= QUICK_GAP_MAX;

        if (matched) {

            tapTimes.clear();
            patternPackage = "";

            return true;
        }

        return false;
    }

    // =========================================================
    // UNLOCK
    // =========================================================

    private void unlockPackage(
            String packageName) {

        unlockedUntil.put(
                packageName,
                SystemClock.elapsedRealtime()
                        + UNLOCK_DURATION
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

    private boolean isUnlocked(
            String packageName) {

        Long expiry =
                unlockedUntil.get(packageName);

        if (expiry == null) {
            return false;
        }

        if (SystemClock.elapsedRealtime()
                < expiry) {

            return true;
        }

        resetPackageState(packageName);

        return false;
    }

    // =========================================================
    // BLOCK ACTION
    // =========================================================

    private void blockNavigation() {

        long now =
                SystemClock.elapsedRealtime();

        if (now - lastBackActionAt
                < BACK_ACTION_GAP) {

            return;
        }

        lastBackActionAt =
                now;

        performGlobalAction(
                GLOBAL_ACTION_BACK
        );
    }

    private String getAppName(
            String packageName) {

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

        handler.removeCallbacksAndMessages(null);
        unlockedUntil.clear();
        tapTimes.clear();

        super.onDestroy();
    }
}

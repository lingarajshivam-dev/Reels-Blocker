package com.example.invisibleblocker;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BlockerAccessibilityService extends AccessibilityService {

    private static final String INSTAGRAM =
            "com.instagram.android";

    private static final String YOUTUBE =
            "com.google.android.youtube";

    private static final String FACEBOOK =
            "com.facebook.katana";

    /*
     * Five fast tab presses unlock the corresponding app.
     */
    private static final int REQUIRED_FAST_TAPS = 6;
    private static final long FAST_TAP_MAX_GAP = 600L;
    private static final long DUPLICATE_EVENT_GAP = 100L;

    /*
     * A player opened soon after switching from one of these
     * applications is considered an external/shared link.
     */
    private static final long EXTERNAL_LINK_WINDOW = 7000L;
    private static final long INSTAGRAM_DM_WINDOW = 6000L;

    /*
     * Prevent duplicate redirection caused by repeated
     * Accessibility events for the same screen.
     */
    private static final long REDIRECT_COOLDOWN = 700L;

    private static final String[] EXTERNAL_LINK_PACKAGES = {
            "com.whatsapp",
            "com.facebook.orca",
            "com.google.android.gm",
            "com.google.android.apps.messaging",
            "com.microsoft.teams",
            "com.microsoft.office.outlook",
            "com.android.chrome",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.opera.browser"
    };

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

    /*
     * The bypass is stored only in memory.
     * Leaving the corresponding app removes it.
     */
    private final Set<String> unlockedPackages =
            new HashSet<>();

    private String foregroundPackage = "";
    private String previousForegroundPackage = "";

    private String tapPackage = "";
    private int fastTapCount = 0;
    private long lastFastTapAt = 0L;

    private String pendingExternalPackage = "";
    private long pendingExternalUntil = 0L;

    private long instagramDmUntil = 0L;

    /*
     * True only while the currently visible player was opened
     * from a permitted DM or external-link source.
     */
    private String allowedExternalPlayerPackage = "";

    /*
     * Ensures the blocker performs only one Back action for
     * each detected player entry.
     */
    private String blockedPlayerPackage = "";
    private boolean blockActionPerformed = false;

    private long lastRedirectAt = 0L;

    private final Runnable screenCheck =
            this::checkCurrentScreen;

    @Override
    public void onAccessibilityEvent(
            AccessibilityEvent event) {

        if (event == null
                || event.getPackageName() == null) {

            return;
        }

        String packageName =
                event.getPackageName().toString();

        int eventType =
                event.getEventType();

        if (eventType
                == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            handleForegroundChange(packageName);
        }

        if (!isSupportedPackage(packageName)) {
            return;
        }

        /*
         * If the corresponding app was unlocked by five fast
         * tab presses, do not block anything until the user
         * leaves that app.
         */
        if (unlockedPackages.contains(packageName)) {
            return;
        }

        /*
         * Remember when an Instagram conversation is visible.
         */
        if (INSTAGRAM.equals(packageName)) {

            AccessibilityNodeInfo root =
                    getRootInActiveWindow();

            if (root != null
                    && containsVisibleId(
                            root,
                            INSTAGRAM_DM_IDS)) {

                instagramDmUntil =
                        SystemClock.elapsedRealtime()
                                + INSTAGRAM_DM_WINDOW;
            }
        }

        boolean clicked =
                eventType
                        == AccessibilityEvent.TYPE_VIEW_CLICKED;

        boolean selected =
                eventType
                        == AccessibilityEvent.TYPE_VIEW_SELECTED;

        /*
         * Detect taps on dedicated Reels/Shorts navigation.
         */
        if (clicked || selected) {

            String label =
                    getCombinedEventLabel(event);

            String viewIds =
                    getSourceViewIds(event);

            if (isShortVideoTab(
                    packageName,
                    label,
                    viewIds)) {

                /*
                 * A direct tab press is never treated as a DM
                 * or external-link entry.
                 */
                clearExternalAllowance();

                if (clicked
                        && registerFastTabPress(packageName)) {

                    unlockPackage(packageName);
                    return;
                }

                beginNewBlockCycle(packageName);
                scheduleScreenChecks();

                return;
            }
        }

        /*
         * Detect Shorts/Reels players opened from the app's
         * home feed, search, profile, or other internal screen.
         */
        scheduleScreenChecks();
    }

    // =========================================================
    // FOREGROUND TRACKING
    // =========================================================

    private void handleForegroundChange(
            String newPackage) {

        if (newPackage == null
                || newPackage.isEmpty()) {

            return;
        }

        String oldPackage =
                foregroundPackage;

        /*
         * Leaving or minimising a supported app immediately
         * resets its five-tap bypass.
         */
        if (!oldPackage.isEmpty()
                && isSupportedPackage(oldPackage)
                && !oldPackage.equals(newPackage)) {

            unlockedPackages.remove(oldPackage);
            resetTapCounter(oldPackage);
            resetBlockCycle();
            clearExternalAllowance();
        }

        /*
         * Only trusted messaging, email and browser packages
         * can create an external-link allowance.
         *
         * Opening Instagram/YouTube/Facebook from the launcher
         * does not create an allowance.
         */
        if (isTrustedExternalSource(oldPackage)
                && isSupportedPackage(newPackage)) {

            pendingExternalPackage =
                    newPackage;

            pendingExternalUntil =
                    SystemClock.elapsedRealtime()
                            + EXTERNAL_LINK_WINDOW;
        }

        previousForegroundPackage =
                oldPackage;

        foregroundPackage =
                newPackage;
    }

    private boolean isSupportedPackage(
            String packageName) {

        return INSTAGRAM.equals(packageName)
                || YOUTUBE.equals(packageName)
                || FACEBOOK.equals(packageName);
    }

    private boolean isTrustedExternalSource(
            String packageName) {

        if (packageName == null
                || packageName.isEmpty()) {

            return false;
        }

        for (String trustedPackage :
                EXTERNAL_LINK_PACKAGES) {

            if (trustedPackage.equals(packageName)) {
                return true;
            }
        }

        return false;
    }

    // =========================================================
    // FIVE FAST TAB PRESSES
    // =========================================================

    private boolean registerFastTabPress(
            String packageName) {

        long now =
                SystemClock.elapsedRealtime();

        if (!packageName.equals(tapPackage)) {

            tapPackage =
                    packageName;

            fastTapCount =
                    0;

            lastFastTapAt =
                    0L;
        }

        if (lastFastTapAt > 0L) {

            long gap =
                    now - lastFastTapAt;

            /*
             * Ignore duplicate events from one physical tap.
             */
            if (gap < DUPLICATE_EVENT_GAP) {
                return false;
            }

            /*
             * The presses must remain quick.
             */
            if (gap > FAST_TAP_MAX_GAP) {
                fastTapCount = 0;
            }
        }

        fastTapCount++;
        lastFastTapAt = now;

        if (fastTapCount >= REQUIRED_FAST_TAPS) {

            fastTapCount = 0;
            lastFastTapAt = 0L;

            return true;
        }

        return false;
    }

    private void unlockPackage(
            String packageName) {

        unlockedPackages.add(packageName);

        resetBlockCycle();
        clearExternalAllowance();

        Toast.makeText(
                this,
                getAppName(packageName)
                        + " unlocked until the app is closed",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void resetTapCounter(
            String packageName) {

        if (packageName.equals(tapPackage)) {

            tapPackage = "";
            fastTapCount = 0;
            lastFastTapAt = 0L;
        }
    }

    // =========================================================
    // EXTERNAL AND DM LINKS
    // =========================================================

    private boolean isExternalEntryActive(
            String packageName) {

        return packageName.equals(
                pendingExternalPackage)
                && SystemClock.elapsedRealtime()
                <= pendingExternalUntil;
    }

    private boolean isInstagramDmEntryActive() {

        return SystemClock.elapsedRealtime()
                <= instagramDmUntil;
    }

    private void clearExternalAllowance() {

        pendingExternalPackage = "";
        pendingExternalUntil = 0L;

        instagramDmUntil = 0L;

        allowedExternalPlayerPackage = "";
    }

    // =========================================================
    // SCREEN CHECKING
    // =========================================================

    private void scheduleScreenChecks() {

        handler.removeCallbacks(screenCheck);

        /*
         * Several short checks handle asynchronously-rendered
         * Shorts/Reels players.
         */
        handler.postDelayed(screenCheck, 80L);
        handler.postDelayed(screenCheck, 250L);
        handler.postDelayed(screenCheck, 550L);
    }

    private void checkCurrentScreen() {

        AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null
                || root.getPackageName() == null) {

            return;
        }

        String packageName =
                root.getPackageName().toString();

        if (!isSupportedPackage(packageName)
                || unlockedPackages.contains(packageName)) {

            return;
        }

        boolean playerVisible =
                isShortVideoPlayerVisible(
                        root,
                        packageName
                );

        /*
         * The player has closed, so reset state and permit a
         * new independent detection later.
         */
        if (!playerVisible) {

            if (packageName.equals(
                    allowedExternalPlayerPackage)) {

                allowedExternalPlayerPackage = "";
            }

            if (packageName.equals(
                    blockedPlayerPackage)) {

                resetBlockCycle();
            }

            return;
        }

        /*
         * Explicit external direct link from WhatsApp,
         * Messenger, browser, SMS or email.
         */
        if (isExternalEntryActive(packageName)) {

            allowedExternalPlayerPackage =
                    packageName;

            pendingExternalPackage = "";
            pendingExternalUntil = 0L;

            return;
        }

        /*
         * Instagram Reel opened from a recently visible DM.
         */
        if (INSTAGRAM.equals(packageName)
                && isInstagramDmEntryActive()) {

            allowedExternalPlayerPackage =
                    packageName;

            instagramDmUntil = 0L;

            return;
        }

        /*
         * Continue allowing only the same currently-visible
         * externally opened player.
         */
        if (packageName.equals(
                allowedExternalPlayerPackage)) {

            return;
        }

        /*
         * This is an internally opened Reels/Shorts player.
         * Perform one Back action for this player entry.
         */
        if (!packageName.equals(
                blockedPlayerPackage)) {

            beginNewBlockCycle(packageName);
        }

        performOneBackForCurrentPlayer(
                packageName
        );
    }

    private void beginNewBlockCycle(
            String packageName) {

        blockedPlayerPackage =
                packageName;

        blockActionPerformed =
                false;
    }

    private void resetBlockCycle() {

        blockedPlayerPackage = "";
        blockActionPerformed = false;
        lastRedirectAt = 0L;
    }

    private void performOneBackForCurrentPlayer(
            String packageName) {

        if (blockActionPerformed) {
            return;
        }

        long now =
                SystemClock.elapsedRealtime();

        if (now - lastRedirectAt
                < REDIRECT_COOLDOWN) {

            return;
        }

        blockActionPerformed =
                true;

        lastRedirectAt =
                now;

        /*
         * Only one Back action is generated per player entry.
         * Screen-check retries cannot generate additional Back
         * actions, so the social app is not accidentally exited.
         */
        performGlobalAction(
                GLOBAL_ACTION_BACK
        );
    }

    // =========================================================
    // PLAYER DETECTION
    // =========================================================

    private boolean isShortVideoPlayerVisible(
            AccessibilityNodeInfo root,
            String packageName) {

        if (YOUTUBE.equals(packageName)) {

            if (containsVisibleId(
                    root,
                    YOUTUBE_SHORT_IDS)) {

                return true;
            }

            return hasPlayerSignals(
                    root,
                    true
            );
        }

        if (INSTAGRAM.equals(packageName)) {

            if (containsVisibleId(
                    root,
                    INSTAGRAM_REEL_IDS)) {

                return true;
            }

            return hasPlayerSignals(
                    root,
                    false
            );
        }

        if (FACEBOOK.equals(packageName)) {

            if (containsVisibleId(
                    root,
                    FACEBOOK_REEL_IDS)) {

                return true;
            }

            return hasPlayerSignals(
                    root,
                    false
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

            for (AccessibilityNodeInfo node :
                    nodes) {

                if (node != null
                        && node.isVisibleToUser()) {

                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasPlayerSignals(
            AccessibilityNodeInfo root,
            boolean shortsPlayer) {

        PlayerSignals signals =
                new PlayerSignals();

        scanNodeTree(
                root,
                signals,
                0
        );

        boolean correctLabel =
                shortsPlayer
                        ? signals.shorts
                        : signals.reels;

        int controls = 0;

        if (signals.like) {
            controls++;
        }

        if (signals.comment) {
            controls++;
        }

        if (signals.share) {
            controls++;
        }

        if (signals.remix) {
            controls++;
        }

        return correctLabel
                && controls >= 2;
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
                                + valueOf(
                                        node.getContentDescription())
                                + " "
                                + valueOf(
                                        node.getViewIdResourceName())
                );

        if (!text.isEmpty()) {

            if (text.contains("shorts")
                    || text.contains("reel_watch")
                    || text.contains("reel_recycler")) {

                signals.shorts = true;
            }

            if (text.contains("reels")
                    || text.contains("reels_viewer")
                    || text.contains("reel viewer")) {

                signals.reels = true;
            }

            if (containsActionWord(
                    text,
                    "like")) {

                signals.like = true;
            }

            if (containsActionWord(
                    text,
                    "comment")) {

                signals.comment = true;
            }

            if (containsActionWord(
                    text,
                    "share")) {

                signals.share = true;
            }

            if (containsActionWord(
                    text,
                    "remix")) {

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

            if (child == null) {
                continue;
            }

            scanNodeTree(
                    child,
                    signals,
                    depth + 1
            );

            child.recycle();
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

            int controls = 0;

            if (like) {
                controls++;
            }

            if (comment) {
                controls++;
            }

            if (share) {
                controls++;
            }

            if (remix) {
                controls++;
            }

            return (shorts || reels)
                    && controls >= 3;
        }
    }

    // =========================================================
    // TAB DETECTION
    // =========================================================

    private boolean isShortVideoTab(
            String packageName,
            String label,
            String viewIds) {

        if (YOUTUBE.equals(packageName)) {

            boolean labelMatches =
                    containsWord(
                            label,
                            "shorts"
                    );

            boolean tabContext =
                    viewIds.contains("pivot")
                            || viewIds.contains("bottom")
                            || viewIds.contains("navigation")
                            || viewIds.contains("shorts")
                            || label.contains("tab");

            return labelMatches
                    && tabContext;
        }

        if (INSTAGRAM.equals(packageName)) {

            boolean labelMatches =
                    containsWord(
                            label,
                            "reels"
                    );

            boolean tabContext =
                    viewIds.contains("tab")
                            || viewIds.contains("navigation")
                            || viewIds.contains("clips")
                            || viewIds.contains("reels")
                            || label.contains("tab")
                            || viewIds.isEmpty();

            return labelMatches
                    && tabContext;
        }

        if (FACEBOOK.equals(packageName)) {

            boolean labelMatches =
                    containsWord(
                            label,
                            "reels"
                    )
                            || label.contains("watch reels")
                            || label.contains("video reels");

            boolean tabContext =
                    viewIds.contains("tab")
                            || viewIds.contains("navigation")
                            || viewIds.contains("reels")
                            || viewIds.contains("watch")
                            || viewIds.contains("video")
                            || label.contains("tab")
                            || label.contains("button")
                            || viewIds.isEmpty();

            return labelMatches
                    && tabContext;
        }

        return false;
    }

    // =========================================================
    // EVENT LABEL HELPERS
    // =========================================================

    private String getCombinedEventLabel(
            AccessibilityEvent event) {

        StringBuilder builder =
                new StringBuilder();

        if (event.getText() != null) {

            for (CharSequence value :
                    event.getText()) {

                appendValue(
                        builder,
                        value
                );
            }
        }

        appendValue(
                builder,
                event.getContentDescription()
        );

        AccessibilityNodeInfo current =
                event.getSource();

        int depth = 0;

        while (current != null
                && depth < 6) {

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

        while (current != null
                && depth < 6) {

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

        if (text == null
                || text.isEmpty()) {

            return false;
        }

        return text.equals(word)
                || text.startsWith(word + " ")
                || text.startsWith(word + ",")
                || text.contains(" " + word + " ")
                || text.endsWith(" " + word)
                || text.contains(word + " tab")
                || text.contains(word + " button");
    }

    // =========================================================
    // DISPLAY NAMES
    // =========================================================

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

    // =========================================================
    // SERVICE CALLBACKS
    // =========================================================

    @Override
    public void onInterrupt() {
        // Required callback.
    }

    @Override
    public void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        unlockedPackages.clear();

        super.onDestroy();
    }
}

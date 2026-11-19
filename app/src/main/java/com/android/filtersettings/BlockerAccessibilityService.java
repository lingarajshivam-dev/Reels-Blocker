package com.android.filtersettings;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Locale;

public class BlockerAccessibilityService extends AccessibilityService {

    private static final String INSTAGRAM = "com.instagram.android";
    private static final String YOUTUBE = "com.google.android.youtube";
    private static final String FACEBOOK = "com.facebook.katana";

    private static final long EXTERNAL_LINK_WINDOW = 7000L;
    private static final long INSTAGRAM_DM_WINDOW = 6000L;
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
        FACEBOOK + ":id/watch_reels_container",

        FACEBOOK + ":id/reels_tab",
        FACEBOOK + ":id/reels_feed",
        FACEBOOK + ":id/reels_fragment",
        FACEBOOK + ":id/reels_video_view",
        FACEBOOK + ":id/reel_player",
        FACEBOOK + ":id/reel_container"
};

    private static final String[] INSTAGRAM_DM_IDS = {
            INSTAGRAM + ":id/row_thread_composer_edittext",
            INSTAGRAM + ":id/direct_thread_container",
            INSTAGRAM + ":id/direct_thread_header",
            INSTAGRAM + ":id/thread_title",
            INSTAGRAM + ":id/direct_thread_recycler_view"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable screenCheck = this::checkCurrentScreen;

    private String foregroundPackage = "";
    private String pendingExternalPackage = "";
    private long pendingExternalUntil = 0L;
    private long instagramDmUntil = 0L;
    private String allowedExternalPlayerPackage = "";
    private String blockedPlayerPackage = "";
    private boolean blockActionPerformed = false;
    private long lastRedirectAt = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) {
            return;
        }

        String packageName = event.getPackageName().toString();
        int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleForegroundChange(packageName);
        }

        if (!isSupportedPackage(packageName)) {
            return;
        }

        if (INSTAGRAM.equals(packageName)) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && containsVisibleId(root, INSTAGRAM_DM_IDS)) {
                instagramDmUntil = SystemClock.elapsedRealtime() + INSTAGRAM_DM_WINDOW;
            }
        }

        boolean clicked = eventType == AccessibilityEvent.TYPE_VIEW_CLICKED;
        boolean selected = eventType == AccessibilityEvent.TYPE_VIEW_SELECTED;

        if (clicked || selected) {
            String label = getCombinedEventLabel(event);
            String viewIds = getSourceViewIds(event);

            if (isShortVideoTab(packageName, label, viewIds)) {
                clearExternalAllowance();
                beginNewBlockCycle(packageName);
                scheduleScreenChecks();
                return;
            }
        }

        scheduleScreenChecks();
    }

    private boolean isSupportedPackage(String packageName) {
        return INSTAGRAM.equals(packageName)
                || YOUTUBE.equals(packageName)
                || FACEBOOK.equals(packageName);
    }

    private void handleForegroundChange(String newPackage) {
        if (newPackage == null || newPackage.isEmpty()) {
            return;
        }

        String oldPackage = foregroundPackage;

        if (!oldPackage.isEmpty()
                && isSupportedPackage(oldPackage)
                && !oldPackage.equals(newPackage)) {
            resetBlockCycle();
            clearExternalAllowance();
        }

        if (isTrustedExternalSource(oldPackage)
                && isSupportedPackage(newPackage)) {
            pendingExternalPackage = newPackage;
            pendingExternalUntil = SystemClock.elapsedRealtime() + EXTERNAL_LINK_WINDOW;
        }

        foregroundPackage = newPackage;
    }

    private boolean isTrustedExternalSource(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        for (String trustedPackage : EXTERNAL_LINK_PACKAGES) {
            if (trustedPackage.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExternalEntryActive(String packageName) {
        return packageName.equals(pendingExternalPackage)
                && SystemClock.elapsedRealtime() <= pendingExternalUntil;
    }

    private boolean isInstagramDmEntryActive() {
        return SystemClock.elapsedRealtime() <= instagramDmUntil;
    }

    private void clearExternalAllowance() {
        pendingExternalPackage = "";
        pendingExternalUntil = 0L;
        instagramDmUntil = 0L;
        allowedExternalPlayerPackage = "";
    }

    private void scheduleScreenChecks() {
        handler.removeCallbacks(screenCheck);
        handler.postDelayed(screenCheck, 80L);
        handler.postDelayed(screenCheck, 250L);
        handler.postDelayed(screenCheck, 550L);
    }

    private void checkCurrentScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) {
            return;
        }

        String packageName = root.getPackageName().toString();
        if (!isSupportedPackage(packageName)) {
            return;
        }

        boolean playerVisible = isShortVideoPlayerVisible(root, packageName);

        if (!playerVisible) {
            if (packageName.equals(allowedExternalPlayerPackage)) {
                allowedExternalPlayerPackage = "";
            }
            if (packageName.equals(blockedPlayerPackage)) {
                resetBlockCycle();
            }
            return;
        }

        if (isExternalEntryActive(packageName)) {
            allowedExternalPlayerPackage = packageName;
            pendingExternalPackage = "";
            pendingExternalUntil = 0L;
            return;
        }

        if (INSTAGRAM.equals(packageName) && isInstagramDmEntryActive()) {
            allowedExternalPlayerPackage = packageName;
            instagramDmUntil = 0L;
            return;
        }

        if (packageName.equals(allowedExternalPlayerPackage)) {
            return;
        }

        if (!packageName.equals(blockedPlayerPackage)) {
            beginNewBlockCycle(packageName);
        }

        performOneBackForCurrentPlayer();
    }

    private void beginNewBlockCycle(String packageName) {
        blockedPlayerPackage = packageName;
        blockActionPerformed = false;
    }

    private void resetBlockCycle() {
        blockedPlayerPackage = "";
        blockActionPerformed = false;
        lastRedirectAt = 0L;
    }

    private void performOneBackForCurrentPlayer() {
        if (blockActionPerformed) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastRedirectAt < REDIRECT_COOLDOWN) {
            return;
        }

        blockActionPerformed = true;
        lastRedirectAt = now;
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    private boolean isShortVideoPlayerVisible(
            AccessibilityNodeInfo root,
            String packageName) {

        if (YOUTUBE.equals(packageName)) {
            return containsVisibleId(root, YOUTUBE_SHORT_IDS)
                    || hasPlayerSignals(root, true);
        }

        if (INSTAGRAM.equals(packageName)) {
            return containsVisibleId(root, INSTAGRAM_REEL_IDS)
                    || hasPlayerSignals(root, false);
        }

        if (FACEBOOK.equals(packageName)) {
            return containsVisibleId(root, FACEBOOK_REEL_IDS)
                    || hasPlayerSignals(root, false);
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
                if (node != null && node.isVisibleToUser()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasPlayerSignals(
            AccessibilityNodeInfo root,
            boolean shortsPlayer) {

        PlayerSignals signals = new PlayerSignals();
        scanNodeTree(root, signals, 0);

        boolean correctLabel = shortsPlayer
                ? signals.shorts
                : signals.reels;

        return correctLabel && signals.controlCount() >= 2;
    }

    private void scanNodeTree(
            AccessibilityNodeInfo node,
            PlayerSignals signals,
            int depth) {

        if (node == null || depth > 35 || signals.isComplete()) {
            return;
        }

        String text = normalize(
                valueOf(node.getText()) + " "
                        + valueOf(node.getContentDescription()) + " "
                        + valueOf(node.getViewIdResourceName())
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

        int childCount = node.getChildCount();
        for (int index = 0; index < childCount; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            scanNodeTree(child, signals, depth + 1);
            child.recycle();
        }
    }

    private boolean containsActionWord(String text, String word) {
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

        int controlCount() {
            int controls = 0;
            if (like) controls++;
            if (comment) controls++;
            if (share) controls++;
            if (remix) controls++;
            return controls;
        }

        boolean isComplete() {
            return (shorts || reels) && controlCount() >= 3;
        }
    }

    private boolean isShortVideoTab(
            String packageName,
            String label,
            String viewIds) {

        if (YOUTUBE.equals(packageName)) {
            boolean labelMatches = containsWord(label, "shorts");
            boolean tabContext = viewIds.contains("pivot")
                    || viewIds.contains("bottom")
                    || viewIds.contains("navigation")
                    || viewIds.contains("shorts")
                    || label.contains("tab");
            return labelMatches && tabContext;
        }

        if (INSTAGRAM.equals(packageName)) {
            boolean labelMatches = containsWord(label, "reels");
            boolean tabContext = viewIds.contains("tab")
                    || viewIds.contains("navigation")
                    || viewIds.contains("clips")
                    || viewIds.contains("reels")
                    || label.contains("tab")
                    || viewIds.isEmpty();
            return labelMatches && tabContext;
        }

        if (FACEBOOK.equals(packageName)) {
            boolean labelMatches = containsWord(label, "reels")
                    || label.contains("watch reels")
                    || label.contains("video reels");
            boolean tabContext = viewIds.contains("tab")
                    || viewIds.contains("navigation")
                    || viewIds.contains("reels")
                    || viewIds.contains("watch")
                    || viewIds.contains("video")
                    || label.contains("tab")
                    || label.contains("button")
                    || viewIds.isEmpty();
            return labelMatches && tabContext;
        }

        return false;
    }

    private String getCombinedEventLabel(AccessibilityEvent event) {
        StringBuilder builder = new StringBuilder();

        if (event.getText() != null) {
            for (CharSequence value : event.getText()) {
                appendValue(builder, value);
            }
        }

        appendValue(builder, event.getContentDescription());

        AccessibilityNodeInfo current = event.getSource();
        int depth = 0;

        while (current != null && depth < 6) {
            appendValue(builder, current.getText());
            appendValue(builder, current.getContentDescription());
            appendValue(builder, current.getViewIdResourceName());

            AccessibilityNodeInfo parent = current.getParent();
            if (depth > 0) {
                current.recycle();
            }
            current = parent;
            depth++;
        }

        if (current != null) {
            current.recycle();
        }

        return normalize(builder.toString());
    }

    private String getSourceViewIds(AccessibilityEvent event) {
        StringBuilder builder = new StringBuilder();
        AccessibilityNodeInfo current = event.getSource();
        int depth = 0;

        while (current != null && depth < 6) {
            appendValue(builder, current.getViewIdResourceName());

            AccessibilityNodeInfo parent = current.getParent();
            if (depth > 0) {
                current.recycle();
            }
            current = parent;
            depth++;
        }

        if (current != null) {
            current.recycle();
        }

        return normalize(builder.toString());
    }

    private void appendValue(StringBuilder builder, CharSequence value) {
        if (value == null) {
            return;
        }

        String text = value.toString().trim();
        if (!text.isEmpty()) {
            builder.append(text).append(" ");
        }
    }

    private String valueOf(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private boolean containsWord(String text, String word) {
        if (text == null || text.isEmpty()) {
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

    @Override
    public void onInterrupt() {
        // Required callback.
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

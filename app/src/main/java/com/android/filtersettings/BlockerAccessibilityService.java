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
    private static final long ACTION_COOLDOWN = 700L;

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

    private static final String[] FACEBOOK_HOME_TARGETS = {
            "home",
            "home tab",
            "facebook home",
            "feed",
            "news feed"
    };

    private static final String[] FACEBOOK_SAFE_TARGETS = {
            "menu",
            "menu tab",
            "profile",
            "your profile",
            "profile tab"
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
    private long lastActionAt = 0L;

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

                if (FACEBOOK.equals(packageName)) {
                    redirectFacebookAwayFromReels();
                } else {
                    performOneBackForCurrentPlayer();
                }

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

        if (isTrustedExternalSource(oldPackage) && isSupportedPackage(newPackage)) {
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
        handler.postDelayed(screenCheck, 900L);
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

        if (FACEBOOK.equals(packageName)) {
            redirectFacebookAwayFromReels();
        } else {
            performOneBackForCurrentPlayer();
        }
    }

    private void beginNewBlockCycle(String packageName) {
        blockedPlayerPackage = packageName;
        blockActionPerformed = false;
    }

    private void resetBlockCycle() {
        blockedPlayerPackage = "";
        blockActionPerformed = false;
        lastActionAt = 0L;
    }

    private void performOneBackForCurrentPlayer() {
        if (blockActionPerformed) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastActionAt < ACTION_COOLDOWN) {
            return;
        }
        blockActionPerformed = true;
        lastActionAt = now;
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    private void redirectFacebookAwayFromReels() {
        if (blockActionPerformed) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastActionAt < ACTION_COOLDOWN) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return;
        }

        boolean clicked = findAndClickFacebookDestination(root, FACEBOOK_HOME_TARGETS, 0);
        if (!clicked) {
            clicked = findAndClickFacebookDestination(root, FACEBOOK_SAFE_TARGETS, 0);
        }

        if (clicked) {
            blockActionPerformed = true;
            lastActionAt = now;
        }
        // No Back fallback for Facebook. This prevents Facebook from exiting
        // when it opens directly into its previously selected Reels surface.
    }

    private boolean findAndClickFacebookDestination(
            AccessibilityNodeInfo node,
            String[] targets,
            int depth) {

        if (node == null || depth > 35) {
            return false;
        }

        String nodeText = getNodeText(node);
        for (String target : targets) {
            if (matchesDestination(nodeText, target)) {
                AccessibilityNodeInfo clickable = findClickableParent(node);
                if (clickable != null) {
                    boolean result = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if (clickable != node) {
                        clickable.recycle();
                    }
                    if (result) {
                        return true;
                    }
                }
            }
        }

        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) {
                continue;
            }
            boolean clicked = findAndClickFacebookDestination(child, targets, depth + 1);
            child.recycle();
            if (clicked) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDestination(String text, String target) {
        return text.equals(target)
                || text.startsWith(target + " ")
                || text.startsWith(target + ",")
                || text.contains(" " + target + " tab")
                || text.contains(target + " button")
                || text.contains("tab_" + target.replace(" ", "_"))
                || text.contains(target.replace(" ", "_") + "_tab")
                || text.contains(":id/" + target.replace(" ", "_"));
    }

    private AccessibilityNodeInfo findClickableParent(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        int depth = 0;
        while (current != null && depth < 5) {
            if (current.isClickable() && current.isEnabled() && current.isVisibleToUser()) {
                return current;
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (current != node) {
                current.recycle();
            }
            current = parent;
            depth++;
        }
        return null;
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
                    || hasFacebookReelSignals(root);
        }
        return false;
    }

    private boolean containsVisibleId(AccessibilityNodeInfo root, String[] ids) {
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
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

    private boolean hasPlayerSignals(AccessibilityNodeInfo root, boolean shortsPlayer) {
        PlayerSignals signals = new PlayerSignals();
        scanNodeTree(root, signals, 0);
        boolean correctLabel = shortsPlayer ? signals.shorts : signals.reels;
        return correctLabel && signals.controlCount() >= 2;
    }

    private void scanNodeTree(AccessibilityNodeInfo node, PlayerSignals signals, int depth) {
        if (node == null || depth > 35 || signals.isComplete()) {
            return;
        }
        String text = getNodeText(node);
        if (text.contains("shorts") || text.contains("reel_watch") || text.contains("reel_recycler")) {
            signals.shorts = true;
        }
        if (text.contains("reels") || text.contains("reels_viewer") || text.contains("reel viewer")) {
            signals.reels = true;
        }
        signals.like |= containsActionWord(text, "like");
        signals.comment |= containsActionWord(text, "comment");
        signals.share |= containsActionWord(text, "share");
        signals.remix |= containsActionWord(text, "remix");

        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            scanNodeTree(child, signals, depth + 1);
            child.recycle();
        }
    }

    private static class PlayerSignals {
        boolean shorts, reels, like, comment, share, remix;
        int controlCount() {
            int count = 0;
            if (like) count++;
            if (comment) count++;
            if (share) count++;
            if (remix) count++;
            return count;
        }
        boolean isComplete() {
            return (shorts || reels) && controlCount() >= 3;
        }
    }

    private boolean hasFacebookReelSignals(AccessibilityNodeInfo root) {
        FacebookSignals signals = new FacebookSignals();
        scanFacebookTree(root, signals, 0);
        if (signals.reels) {
            return signals.controlCount() >= 1;
        }
        return signals.videoPlayer && signals.controlCount() >= 2;
    }

    private void scanFacebookTree(AccessibilityNodeInfo node, FacebookSignals signals, int depth) {
        if (node == null || depth > 35 || signals.isComplete()) {
            return;
        }
        String text = getNodeText(node);
        if (text.contains("reels") || text.contains("reel_")
                || text.contains("reels_") || text.contains("watch reels")) {
            signals.reels = true;
        }
        if (text.contains("video") || text.contains("watch")
                || text.contains("player") || text.contains("fullscreen")) {
            signals.videoPlayer = true;
        }
        signals.like |= containsActionWord(text, "like");
        signals.comment |= containsActionWord(text, "comment");
        signals.share |= containsActionWord(text, "share");
        signals.follow |= containsActionWord(text, "follow");

        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            scanFacebookTree(child, signals, depth + 1);
            child.recycle();
        }
    }

    private static class FacebookSignals {
        boolean reels, videoPlayer, like, comment, share, follow;
        int controlCount() {
            int count = 0;
            if (like) count++;
            if (comment) count++;
            if (share) count++;
            if (follow) count++;
            return count;
        }
        boolean isComplete() {
            return (reels && controlCount() >= 2)
                    || (videoPlayer && controlCount() >= 3);
        }
    }

    private boolean isShortVideoTab(String packageName, String label, String viewIds) {
        if (YOUTUBE.equals(packageName)) {
            return containsWord(label, "shorts")
                    && (viewIds.contains("pivot") || viewIds.contains("bottom")
                    || viewIds.contains("navigation") || viewIds.contains("shorts")
                    || label.contains("tab"));
        }
        if (INSTAGRAM.equals(packageName)) {
            return containsWord(label, "reels")
                    && (viewIds.contains("tab") || viewIds.contains("navigation")
                    || viewIds.contains("clips") || viewIds.contains("reels")
                    || label.contains("tab") || viewIds.isEmpty());
        }
        if (FACEBOOK.equals(packageName)) {
            boolean labelMatches = containsWord(label, "reels")
                    || label.contains("watch reels")
                    || label.contains("video reels")
                    || label.contains("reels and short videos")
                    || viewIds.contains("reels");
            boolean tabContext = viewIds.contains("tab")
                    || viewIds.contains("navigation")
                    || viewIds.contains("reels")
                    || viewIds.contains("watch")
                    || viewIds.contains("video")
                    || viewIds.contains("pivot")
                    || label.contains("tab")
                    || label.contains("button")
                    || viewIds.isEmpty();
            return labelMatches && tabContext;
        }
        return false;
    }

    private String getNodeText(AccessibilityNodeInfo node) {
        if (node == null) return "";
        return normalize(valueOf(node.getText()) + " "
                + valueOf(node.getContentDescription()) + " "
                + valueOf(node.getViewIdResourceName()));
    }

    private boolean containsActionWord(String text, String word) {
        return text.equals(word)
                || text.startsWith(word + " ")
                || text.startsWith(word + ",")
                || text.contains(" " + word + " ")
                || text.endsWith(" " + word)
                || text.contains(word + " button");
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
            if (depth > 0) current.recycle();
            current = parent;
            depth++;
        }
        if (current != null) current.recycle();
        return normalize(builder.toString());
    }

    private String getSourceViewIds(AccessibilityEvent event) {
        StringBuilder builder = new StringBuilder();
        AccessibilityNodeInfo current = event.getSource();
        int depth = 0;
        while (current != null && depth < 6) {
            appendValue(builder, current.getViewIdResourceName());
            AccessibilityNodeInfo parent = current.getParent();
            if (depth > 0) current.recycle();
            current = parent;
            depth++;
        }
        if (current != null) current.recycle();
        return normalize(builder.toString());
    }

    private void appendValue(StringBuilder builder, CharSequence value) {
        if (value == null) return;
        String text = value.toString().trim();
        if (!text.isEmpty()) builder.append(text).append(' ');
    }

    private String valueOf(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private String normalize(String value) {
        return value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private boolean containsWord(String text, String word) {
        if (text == null || text.isEmpty()) return false;
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

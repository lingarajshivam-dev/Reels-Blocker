package com.example.invisibleblocker;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Locale;

public class BlockerAccessibilityService extends AccessibilityService {

    private static final String IG = "com.instagram.android";
    private static final String YT = "com.google.android.youtube";

    // Full-screen Reels / Shorts player containers
    private static final String[] IG_IDS = {
            IG + ":id/clips_viewer_view_pager",
            IG + ":id/clips_viewer_container",
            IG + ":id/root_clips_layout",
            IG + ":id/clips_video_container"
    };

    private static final String[] YT_IDS = {
            YT + ":id/reel_recycler",
            YT + ":id/reel_player_page_container",
            YT + ":id/reel_watch_player",
            YT + ":id/reel_watch_fragment_root"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable recheck = this::checkScreen;

    private long lastActionAt = 0L;
    private int attempts = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();
        if (!pkg.equals(IG) && !pkg.equals(YT)) return;

        int type = event.getEventType();

        // 1) Tap on the "Reels" / "Shorts" tab (including repeated taps)
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            if (isBlockedLabel(pkg, labelOf(event))) {
                block();
                return;
            }
        }

        // 2) Any screen change: check whether a Reels/Shorts player is visible
        handler.removeCallbacks(recheck);
        handler.postDelayed(recheck, 120);
    }

    private String labelOf(AccessibilityEvent event) {
        CharSequence label = (event.getText() != null && !event.getText().isEmpty())
                ? event.getText().get(0)
                : event.getContentDescription();
        return label == null ? "" : label.toString().trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlockedLabel(String pkg, String text) {
        if (pkg.equals(YT)) {
            return text.equals("shorts") || text.equals("youtube shorts");
        }
        return text.equals("reels") || text.equals("instagram reels");
    }

    private void checkScreen() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null) return;

        String pkg = root.getPackageName().toString();
        String[] ids = pkg.equals(IG) ? IG_IDS : pkg.equals(YT) ? YT_IDS : null;
        if (ids == null) {
            attempts = 0;
            return;
        }

        if (hasVisibleNode(root, ids)) {
            block();
        } else {
            attempts = 0; // successfully left the Reels/Shorts screen
        }
    }

    private boolean hasVisibleNode(AccessibilityNodeInfo root, String[] ids) {
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes == null) continue;
            for (AccessibilityNodeInfo n : nodes) {
                if (n != null && n.isVisibleToUser()) return true;
            }
        }
        return false;
    }

    private void block() {
        long now = SystemClock.elapsedRealtime();

        // Too soon after the last action: re-check shortly instead of ignoring
        if (now - lastActionAt < 300) {
            handler.removeCallbacks(recheck);
            handler.postDelayed(recheck, 350);
            return;
        }

        lastActionAt = now;
        attempts++;

        if (attempts >= 3) {
            // Back is not leaving the Reels/Shorts tab, so go to the phone's home screen
            performGlobalAction(GLOBAL_ACTION_HOME);
            attempts = 0;
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }

        // Confirm the screen was actually left
        handler.removeCallbacks(recheck);
        handler.postDelayed(recheck, 450);
    }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

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

    private static final String IG = "com.instagram.android";
    private static final String YT = "com.google.android.youtube";

    // ---- Secret unlock pattern: (tap+tap) pause (tap+tap) ----
    private static final long QUICK_GAP_MAX = 600;               // ms between taps in a pair
    private static final long PAUSE_MIN = 1000;                  // ms pause between pairs
    private static final long PAUSE_MAX = 3000;
    private static final long DUPLICATE_MS = 80;                 // ignore double events of one tap
    private static final long UNLOCK_DURATION = 10 * 60 * 1000L; // 10 minutes

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

    private final Map<String, Long> unlockedUntil = new HashMap<>();
    private final List<Long> tapTimes = new ArrayList<>();
    private String tapPkg = "";
    private long lastTabTapAt = 0L;

    private long lastActionAt = 0L;
    private int attempts = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();
        if (!pkg.equals(IG) && !pkg.equals(YT)) return;

        int type = event.getEventType();
        boolean isClick = type == AccessibilityEvent.TYPE_VIEW_CLICKED;
        boolean isSelect = type == AccessibilityEvent.TYPE_VIEW_SELECTED;

        // 1) Tap on the "Reels" / "Shorts" tab
        if ((isClick || isSelect) && isBlockedLabel(pkg, labelOf(event))) {
            long now = SystemClock.elapsedRealtime();
            lastTabTapAt = now;

            if (isClick && registerTap(pkg, now)) {
                unlock(pkg);
                return;
            }
            if (isUnlocked(pkg)) return;
            block();
            return;
        }

        if (isUnlocked(pkg)) return;

        // 2) Any screen change: check whether a Reels/Shorts player is visible
        handler.removeCallbacks(recheck);
        handler.postDelayed(recheck, 120);
    }

    // ================= SECRET PATTERN =================
    /** Returns true when (tap+tap) pause (tap+tap) is completed. */
    private boolean registerTap(String pkg, long now) {
        if (!pkg.equals(tapPkg)) {
            tapTimes.clear();
            tapPkg = pkg;
        }

        if (!tapTimes.isEmpty()) {
            long gap = now - tapTimes.get(tapTimes.size() - 1);
            if (gap < DUPLICATE_MS) return false;   // same tap reported twice
            if (gap > PAUSE_MAX) tapTimes.clear();  // too slow: start over
        }

        tapTimes.add(now);
        while (tapTimes.size() > 4) tapTimes.remove(0);
        if (tapTimes.size() < 4) return false;

        long g1 = tapTimes.get(1) - tapTimes.get(0); // first pair
        long g2 = tapTimes.get(2) - tapTimes.get(1); // pause
        long g3 = tapTimes.get(3) - tapTimes.get(2); // second pair

        boolean firstPair = g1 <= QUICK_GAP_MAX;
        boolean pause = g2 >= PAUSE_MIN && g2 <= PAUSE_MAX;
        boolean secondPair = g3 <= QUICK_GAP_MAX;

        if (firstPair && pause && secondPair) {
            tapTimes.clear();
            return true;
        }
        return false;
    }

    private void unlock(String pkg) {
        unlockedUntil.put(pkg, SystemClock.elapsedRealtime() + UNLOCK_DURATION);
        handler.removeCallbacks(recheck);
        attempts = 0;
        Toast.makeText(this, "Unlocked for 10 min", Toast.LENGTH_SHORT).show();
    }

    private boolean isUnlocked(String pkg) {
        Long until = unlockedUntil.get(pkg);
        if (until == null) return false;
        if (SystemClock.elapsedRealtime() < until) return true;
        unlockedUntil.remove(pkg); // expired: blocking resumes
        return false;
    }

    // ================= DETECTION =================
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
        if (ids == null || isUnlocked(pkg)) {
            attempts = 0;
            return;
        }

        if (hasVisibleNode(root, ids)) {
            block();
        } else {
            attempts = 0;
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

    // ================= BLOCK =================
    private void block() {
        long now = SystemClock.elapsedRealtime();

        if (now - lastActionAt < 300) {
            handler.removeCallbacks(recheck);
            handler.postDelayed(recheck, 350);
            return;
        }

        lastActionAt = now;
        attempts++;

        // While the user may be entering the pattern, only use Back (not Home)
        boolean tappingTab = now - lastTabTapAt < PAUSE_MAX + 1000;

        if (attempts >= 3 && !tappingTab) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            attempts = 0;
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }

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

package com.example.invisibleblocker;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.SystemClock;

import java.util.Locale;

/**
 * Minimal prototype. It reacts to clicks on navigation labels rather than
 * continuously scanning screen text, to reduce false positives.
 *
 * UI labels and accessibility trees change between app versions/languages.
 */
public class BlockerAccessibilityService extends AccessibilityService {
    private long lastBlockedAt = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String pkg = event.getPackageName().toString();
        if (!pkg.equals("com.google.android.youtube")
                && !pkg.equals("com.instagram.android")) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_VIEW_CLICKED
                && type != AccessibilityEvent.TYPE_VIEW_SELECTED) return;

        CharSequence label = event.getText() != null && !event.getText().isEmpty()
                ? event.getText().get(0) : event.getContentDescription();
        String text = label == null ? "" : label.toString().trim().toLowerCase(Locale.ROOT);

        boolean block = false;
        if (pkg.equals("com.google.android.youtube")) {
            block = text.equals("shorts") || text.equals("youtube shorts");
        } else if (pkg.equals("com.instagram.android")) {
            block = text.equals("reels") || text.equals("instagram reels");
        }

        if (block) {
            long now = SystemClock.elapsedRealtime();
            if (now - lastBlockedAt > 700) {
                lastBlockedAt = now;
                // Accessibility services cannot cancel another app's click directly.
                // Return immediately after the destination starts opening.
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        }
    }

    @Override
    public void onInterrupt() { }
}

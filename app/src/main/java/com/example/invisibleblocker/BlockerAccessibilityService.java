package com.android.filtersettings;

import android.accessibilityservice.AccessibilityServi;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Locale;

public class BlockerAccessibilityService extends AccessibilityService {

    private static final String INSTAGRAM =
            "com.instagram.android";

    private static final String YOUTUBE =
            "com.google.android.youtube";

    private static final String FACEBOOK =
            "com.facebook.katana";

    /*
     * A player opened shortly after entering from one of these
     * trusted applications is treated as an external/shared link.
     */
    private static final long EXTERNAL_LINK_WINDOW = 7000L;

    /*
     * Remember an Instagram DM briefly so a Reel opened directly
     * from that conversation can remain available.
     */
    private static final long INSTAGRAM_DM_WINDOW = 6000L;

    /*
     * Prevent repeated Accessibility events from issuing
     * multiple Back actions.
     */
    private static final long RE*IRECT_COOLDOWN = 700L;

    privat* static final String[] EXTERNAL_LI*K_PACKAGES = {
            "com.wh*tsapp",
            "com.facebook.*rca",
            "com.google.andr*id.gm",
            "com.google.an*roid.apps.messaging",
            *com.microsoft.teams",
            *com.microsoft.office.outlook",
   *        "com.android.chrome",
    *       "com.microsoft.emmx",
     *      "com.sec.android.app.sbrowse*",
            "org.mozilla.firefo*",
            "com.brave.browser"*
            "com.opera.browser"
 *  };

    private static final Str*ng[] YOUTUBE_SHORT_IDS = {
       *    YOUTUBE + ":id/reel_watch_frag*ent_root",
            YOUTUBE + "*id/reel_recycler",
            YOU*UBE + ":id/reel_player_page_contai*er",
            YOUTUBE + ":id/re*l_watch_player",
            YOUTU*E + ":id/shorts_player",
         *  YOUTUBE + ":id/shorts_container"*    };

    private static final S*ring[] INSTAGRAM_REEL_IDS = {
    *       INSTAGRAM + ":id/clips_view*r_view_pager",
            INSTAGR*M + ":id/clips_viewer_container",
*           INSTAGRAM + ":id/root_c*ips_layout",
            INSTAGRAM*+ ":id/clips_video_container",
   *        INSTAGRAM + ":id/reel_view*r_container",
            INSTAGRA* + ":id/reel_viewer_root"
    };

*   private static final String[] F*CEBOOK_REEL_IDS = {
            FA*EBOOK + ":id/reels_viewer",
      *     FACEBOOK + ":id/reels_viewer_*ontainer",
            FACEBOOK + *:id/reels_player",
            FAC*BOOK + ":id/reels_player_container*,
            FACEBOOK + ":id/reel*_video_container",
            FAC*BOOK + ":id/reel_viewer_root",
   *        FACEBOOK + ":id/reel_video*container",
            FACEBOOK +*":id/watch_reels_container"
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

    private String foregroundPackage = "";

    /*
     * External/shared-link tracking.
     */
    private String pendingExternalPackage = "";
    private long pendingExternalUntil = 0L;

    /*
     * Instagram DM tracking.
     */
    private long instagramDmUntil = 0L;

    /*
     * Represents the currently visible Reel/Short that was
     * legitimately opened from an approved DM/external source.
     */
    private String allowedExternalPlayerPackage = "";

    /*
     * One Back maximum per internally opened player.
     */
    private String blockedPlayer*ackage = "";
    private boolean b*ockActionPerformed = false;
    pr*vate long lastRedirectAt = 0L;

  * private final Runnable screenChec* =
            this::checkCurrentS*reen;

    // =========================================================
    // ACCESSIBILITY EVENTS
    // =========================================================

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

        /*
         * Observe foreground changes from all packages.
         */
        if (eventType
                == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            handleForegroundChange(
                    packageName
            );
        }

        if (!isSupportedPackage(packageName)) {
            return;
        }

        /*
         * Remember when an Instagram DM/conversation
         * is currently visible.
         */
        if (INSTAGRAM.equals(pac*ageName)) {

            Accessibi*ityNodeInfo root =
               *    getRootInActiveWindow();

    *       if (root != null
          *         && containsVisibleId(
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
         * Dedicated Reels/Shorts tab was clicked.
         *
         * There is intentionally NO hidden tap bypass anymore.
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
                 * A tab click is internal navigation and
                 * must not inherit an external-link allowance.
                 */
                clearExternalAllowance();

                beginNewBlockCycle(
                        packageName
                );

                scheduleScreenChecks();

                return;
            }
        }

        /*
         * Also check for Reels/Shorts opened from Home,
         * search, profile or another internal page.
         */
        scheduleScreenChecks();
*   }

    // =====================*==================================*
    // SUPPORTED APPS
    // ====*==================================*=================

    private boo*ean isSupportedPackage(
          * String packageName) {

        re*urn INSTAGRAM.equals(packageName)
*               || YOUTUBE.equals(packageName)
                || FACEBOOK.equals(packageName);
    }

    // =========================================================
    // FOREGROUND / EXTERNAL LINK TRACKING
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
         * Leaving the supported social application clears
         * temporary player state.
         *
         * There is no bypass/unlock state anymore.
         */
        if (!oldPackage.isEmpty()
                && isSupportedPackage(oldPackage)
                && !oldPackage.equals(newPackage)) {

            resetBlockCycle();
            clearExternalAllowance();
        }

        /*
         * Entering Instagram, YouTube or Facebook directly
         * from an approved messaging/email/browser application
         * establishes a short external-link allowance.
         */
        if (isTrustedExternalSou*ce(oldPackage)
                && *sSupportedPackage(newPackage)) {

*           pendingExternalPackage *
                    newPackage;

*           pendingExternalUntil =
*                   SystemClock.ela*sedRealtime()
                    *       + EXTERNAL_LINK_WINDOW;
   *    }

        foregroundPackage =*                newPackage;
    }
*    private boolean isTrustedExter*alSource(
            String packa*eName) {

        if (packageName *= null
                || packageN*me.isEmpty()) {

            retur* false;
        }

        for (St*ing trustedPackage :
             *  EXTERNAL_LINK_PACKAGES) {

     *      if (trustedPackage.equals(pa*kageName)) {
                retur* true;
            }
        }

  *     return false;
    }

    // =*==================================*====================
    // EXTERN*L LINKS / INSTAGRAM DMS
    // ===*==================================*==================

    private bo*lean isExternalEntryActive(
      *     String packageName) {

      * return packageName.equals(
      *         pendingExternalPackage)
 *              && SystemClock.elaps*dRealtime()
                <= pen*ingExternalUntil;
    }

    priva*e boolean isInstagramDmEntryActive*) {

        return SystemClock.el*psedRealtime()
                <= *nstagramDmUntil;
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

        handler.removeCallbacks(
                screenCheck
        );

        /*
         * Apps do not always render the player immediately.
         * Check several times over the next half-second.
         */
        handler.postDelayed(
   *            screenCheck,
         *      80L
        );

        hand*er.postDelayed(
                sc*eenCheck,
                250L
   *    );

        handler.postDelaye*(
                screenCheck,
   *            550L
        );
    }
*    private void checkCurrentScree*() {

        AccessibilityNodeInfo root =
                getRootInActiveWindow();

        if (root == null
                || root.getPackageName() == null) {

            return;
        }

        String packageName =
                root.getPackageName().toString();

        if (!isSupportedPackage(packageName)) {
            return;
        }

        boolean playerVisible =
                isShortVideoPlayerVisible(
                        root,
                        packageName
                );

        /*
         * Player is gone.
         *
         * Reset this player cycle so the next internally opened
         * Reel/Short can independently trigger one Back action.
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
         * Permit a Reel/Short opened from a trusted external app.
         */
        if (isExternalEntryActive(packageName)) {

            allowedExternalPlayerPackage =
                    packageName;

            pendingExternalPackage = "";
            pendingExternalUntil = 0L;

            return;
        }

        /*
         * Permit an Instagram Reel opened from a recent DM.
         */
        if (INSTAGRAM.equals(packageName)
                && isInstagramDmEntryActive()) {

            allowedExternalPlayerPackage =
                    packageName;

            instagramDmUntil = 0L;

            return;
        }

        /*
         * Continue allowing the current externally initiated
         * player until the player disappears.
         */
        if (packageName.equals(
                allowedExternalPlayerPackage)) {

            return;
        }

        /*
         * Internal Reel/Short:
         *
         * - dedicated tab
         * - application Home/feed
         * - profile
         * - search
         * - another internal navigation path
         */
        if (!packageName.equals(*                blockedPlayerPacka*e)) {

            beginNewBlockCy*le(
                    packageName
            );
        }

        performOneBackForCurrentPlayer();
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

    private void performOneBackForCurrentPlayer() {

        /*
         * Critical anti-exit behavior:
         *
         * only ONE global Back action is allowed during the
         * current Reels/Shorts player session.
         */
        if (blockActionPerformed* {
            return;
        }

*       long now =
                *ystemClock.elapsedRealtime();

   *    if (now - lastRedirectAt
                < REDIRECT_COOLDOWN) {

            return;
        }

        blockActionPerformed =
                true;

        lastRedirectAt =
                now;

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
                    root.findAccessibilityNodeInfosByViewId(
                            id
                    );

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

    // =========================================================
    // PLAYER FALLBACK DETECTION
    // =========================================================

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

        return correctLabel
                && signals.controlCount() >= 2;
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

        int controlCount() {

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

            return controls;
        }

        boolean isComplete() {

            return (shorts || reels)
                    && controlCount() >= 3;
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
                            || label.contains(
                                    "watch reels")
                            || label.contains(
                                    "video reels");

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
    // EVENT HELPERS
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
                .replaceAll(
                        "\\s+",
                        " "
                );
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
    // SERVICE CALLBACKS
    // =========================================================

    @Override
    public void onInterrupt() {
        // Required callback.
    }

    @Override
    public void onDestroy() {

        handler.removeCallbacksAndMessages(
                null
        );

        super.onDestroy();
    }
}

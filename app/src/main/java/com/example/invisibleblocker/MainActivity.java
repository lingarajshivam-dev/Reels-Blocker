package com.example.invisibleblocker;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int RED = Color.parseColor("#FF3B5C");
    private static final int PURPLE = Color.parseColor("#A13BFF");
    private static final int GREEN = Color.parseColor("#00E58F");
    private static final int AMBER = Color.parseColor("#FFB020");
    private static final int CARD = Color.parseColor("#1AFFFFFF");
    private static final int TEXT_DIM = Color.parseColor("#B3FFFFFF");

    private View glowRing;
    private TextView statusPill, headline, subText;
    private LinearLayout stepsCard, batteryRow;
    private TextView batteryText, batteryFix;
    private TextView primaryBtn, secondaryBtn;
    private ObjectAnimator pulseX, pulseY;
    private boolean batteryAsked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor("#1A0B2E"));
        w.setNavigationBarColor(Color.parseColor("#0A0A12"));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor("#1A0B2E"), Color.parseColor("#120A24"),
                        Color.parseColor("#0A0A12")}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(40), dp(24), dp(32));
        scroll.addView(root);

        // ---- Icon with glow ring ----
        FrameLayout iconBox = new FrameLayout(this);
        glowRing = new View(this);
        iconBox.addView(glowRing, new FrameLayout.LayoutParams(dp(170), dp(170), Gravity.CENTER));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher);
        GradientDrawable iconShape = new GradientDrawable();
        iconShape.setCornerRadius(dp(30));
        icon.setBackground(iconShape);
        icon.setClipToOutline(true);
        icon.setElevation(dp(12));
        iconBox.addView(icon, new FrameLayout.LayoutParams(dp(120), dp(120), Gravity.CENTER));
        root.addView(iconBox, new LinearLayout.LayoutParams(dp(180), dp(180)));

        // ---- Title ----
        TextView title = text("Reels Blocker", 30, Color.WHITE, true);
        title.setPadding(0, dp(12), 0, dp(4));
        root.addView(title);
        root.addView(text("Take back your time ⏳", 14, TEXT_DIM, false));

        // ---- Status pill ----
        statusPill = text("", 13, Color.WHITE, true);
        statusPill.setLetterSpacing(0.12f);
        statusPill.setPadding(dp(18), dp(8), dp(18), dp(8));
        LinearLayout.LayoutParams pillLp = wrap();
        pillLp.topMargin = dp(20);
        root.addView(statusPill, pillLp);

        // ---- Headline ----
        headline = text("", 22, Color.WHITE, true);
        headline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hLp = wrap();
        hLp.topMargin = dp(20);
        root.addView(headline, hLp);

        subText = text("", 14, TEXT_DIM, false);
        subText.setGravity(Gravity.CENTER);
        subText.setPadding(dp(8), dp(6), dp(8), 0);
        root.addView(subText);

        // ---- Battery row ----
        batteryRow = new LinearLayout(this);
        batteryRow.setOrientation(LinearLayout.HORIZONTAL);
        batteryRow.setGravity(Gravity.CENTER_VERTICAL);
        batteryRow.setPadding(dp(18), dp(14), dp(14), dp(14));
        batteryText = text("", 14, Color.WHITE, false);
        batteryRow.addView(batteryText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        batteryFix = text("Fix", 14, Color.WHITE, true);
        batteryFix.setPadding(dp(18), dp(8), dp(18), dp(8));
        batteryFix.setBackground(rounded(AMBER, dp(20), AMBER));
        batteryFix.setOnClickListener(v -> { press(v); openBatteryPopup(); });
        batteryRow.addView(batteryFix);
        LinearLayout.LayoutParams bLp = match();
        bLp.topMargin = dp(22);
        root.addView(batteryRow, bLp);

        // ---- Steps card ----
        stepsCard = new LinearLayout(this);
        stepsCard.setOrientation(LinearLayout.VERTICAL);
        stepsCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        stepsCard.setBackground(rounded(CARD, dp(20), Color.parseColor("#26FFFFFF")));
        String[] steps = {
                "Tap the button below",
                "Open 'Installed apps' / 'Downloaded apps'",
                "Select 'Invisible Blocker'",
                "Switch it ON and tap Allow",
                "Come back here ✨"};
        for (int i = 0; i < steps.length; i++) stepsCard.addView(stepRow(i + 1, steps[i]));
        LinearLayout.LayoutParams cardLp = match();
        cardLp.topMargin = dp(14);
        root.addView(stepsCard, cardLp);

        // ---- Primary button ----
        primaryBtn = text("", 17, Color.WHITE, true);
        primaryBtn.setGravity(Gravity.CENTER);
        primaryBtn.setPadding(0, dp(17), 0, dp(17));
        GradientDrawable btnBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{RED, PURPLE});
        btnBg.setCornerRadius(dp(30));
        primaryBtn.setBackground(btnBg);
        primaryBtn.setElevation(dp(8));
        primaryBtn.setOnClickListener(v -> { press(v); showDisclosure(); });
        LinearLayout.LayoutParams pLp = match();
        pLp.topMargin = dp(26);
        root.addView(primaryBtn, pLp);

        // ---- Secondary button ----
        secondaryBtn = text("Switch greyed out? Fix it here", 14, Color.WHITE, false);
        secondaryBtn.setGravity(Gravity.CENTER);
        secondaryBtn.setPadding(0, dp(14), 0, dp(14));
        secondaryBtn.setBackground(rounded(Color.TRANSPARENT, dp(30), Color.parseColor("#55FFFFFF")));
        secondaryBtn.setOnClickListener(v -> { press(v); openAppInfo(); });
        LinearLayout.LayoutParams sLp = match();
        sLp.topMargin = dp(12);
        root.addView(secondaryBtn, sLp);

        // ---- Privacy note ----
        TextView privacy = text("🔒 No data is read, stored or shared", 12,
                Color.parseColor("#80FFFFFF"), false);
        LinearLayout.LayoutParams prLp = wrap();
        prLp.topMargin = dp(22);
        root.addView(privacy, prLp);

        setContentView(scroll);

        // Entrance animation
        root.setAlpha(0f);
        root.setTranslationY(dp(30));
        root.animate().alpha(1f).translationY(0).setDuration(600).start();

        // Pulse animation
        pulseX = ObjectAnimator.ofFloat(glowRing, "scaleX", 0.9f, 1.08f);
        pulseY = ObjectAnimator.ofFloat(glowRing, "scaleY", 0.9f, 1.08f);
        for (ObjectAnimator a : new ObjectAnimator[]{pulseX, pulseY}) {
            a.setDuration(1100);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.REVERSE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
        // Automatically ask once per launch to disable battery optimisation
        if (!batteryAsked && !isBatteryUnrestricted()) {
            batteryAsked = true;
            openBatteryPopup();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pulseX.cancel();
        pulseY.cancel();
    }

    // ================= STATUS =================
    private void updateUi() {
        boolean on = isServiceEnabled();
        boolean battOk = isBatteryUnrestricted();
        int color = on ? GREEN : RED;

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ring.setGradientRadius(dp(85));
        ring.setColors(new int[]{withAlpha(color, 110), withAlpha(color, 40), Color.TRANSPARENT});
        glowRing.setBackground(ring);

        statusPill.setText(on ? "●  PROTECTION ACTIVE" : "●  PROTECTION OFF");
        statusPill.setBackground(rounded(withAlpha(color, 45), dp(20), color));
        statusPill.setTextColor(color);

        if (battOk) {
            batteryText.setText("🔋  Battery: Unrestricted ✓");
            batteryRow.setBackground(rounded(withAlpha(GREEN, 30), dp(18), withAlpha(GREEN, 120)));
            batteryFix.setVisibility(View.GONE);
        } else {
            batteryText.setText("🔋  Battery: Restricted");
            batteryRow.setBackground(rounded(withAlpha(AMBER, 30), dp(18), withAlpha(AMBER, 140)));
            batteryFix.setVisibility(View.VISIBLE);
        }

        if (on) {
            headline.setText(battOk ? "You're protected 🎉" : "Almost there!");
            subText.setText(battOk
                    ? "Reels are blocked automatically.\nYou can close this app now."
                    : "Tap 'Fix' so Android doesn't stop the blocker.");
            stepsCard.setVisibility(View.GONE);
            primaryBtn.setText("Open Accessibility Settings");
            secondaryBtn.setVisibility(View.GONE);
        } else {
            headline.setText("One quick step left");
            subText.setText("Turn on Accessibility so the app can block reels.");
            stepsCard.setVisibility(View.VISIBLE);
            primaryBtn.setText("Turn On Protection  →");
            secondaryBtn.setVisibility(Build.VERSION.SDK_INT >= 33 ? View.VISIBLE : View.GONE);
        }

        if (!pulseX.isRunning()) {
            pulseX.start();
            pulseY.start();
        }
    }

    private boolean isBatteryUnrestricted() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean isServiceEnabled() {
        ComponentName expected = new ComponentName(this, BlockerAccessibilityService.class);
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName cn = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(cn)) return true;
        }
        return false;
    }

    // ================= ACTIONS =================
    private void openBatteryPopup() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) { }
        }
    }

    private void showDisclosure() {
        if (isServiceEnabled()) {
            openAccessibilitySettings();
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Accessibility permission")
                .setMessage("Reels Blocker uses Accessibility only to detect when a Reels/Shorts " +
                        "screen opens and close it.\n\nNo personal data is read, stored or shared.")
                .setPositiveButton("Continue", (d, x) -> openAccessibilitySettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        String component = new ComponentName(this, BlockerAccessibilityService.class).flattenToString();
        Bundle args = new Bundle();
        args.putString(":settings:fragment_args_key", component);
        intent.putExtra(":settings:fragment_args_key", component);
        intent.putExtra(":settings:show_fragment_args", args);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openAppInfo() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Allow restricted setting")
                .setMessage("Android 13+ locks Accessibility for apps installed from an APK.\n\n" +
                        "1. On the next screen tap ⋮ (top-right)\n" +
                        "2. Tap 'Allow restricted settings'\n" +
                        "3. Come back and tap 'Turn On Protection'")
                .setPositiveButton("Open App Info", (d, x) -> startActivity(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ================= UI HELPERS =================
    private LinearLayout stepRow(int n, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView num = text(String.valueOf(n), 13, Color.WHITE, true);
        num.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{RED, PURPLE});
        circle.setShape(GradientDrawable.OVAL);
        num.setBackground(circle);
        row.addView(num, new LinearLayout.LayoutParams(dp(28), dp(28)));

        TextView t = text(label, 15, Color.WHITE, false);
        t.setPadding(dp(14), 0, 0, 0);
        row.addView(t);
        return row;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private GradientDrawable rounded(int fill, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        g.setStroke(dp(1), stroke);
        return g;
    }

    private void press(View v) {
        v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start())
                .start();
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}

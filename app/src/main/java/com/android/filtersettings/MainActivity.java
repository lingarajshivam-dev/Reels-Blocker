package com.android.filtersettings;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int RED = Color.parseColor("#FF3B5C");
    private static final int GREEN = Color.parseColor("#00D98B");
    private static final int AMBER = Color.parseColor("#FFB020");
    private static final int TEXT_DIM = Color.parseColor("#B3FFFFFF");
    private static final int CARD = Color.parseColor("#16FFFFFF");

    private View glowRing;
    private TextView statusPill;
    private TextView headline;
    private TextView subText;
    private LinearLayout setupCard;
    private LinearLayout batteryCard;
    private TextView batteryStatusText;
    private TextView batteryActionButton;
    private TextView primaryButton;
    private TextView restrictedButton;
    private TextView hideButton;
    private ObjectAnimator pulseX;
    private ObjectAnimator pulseY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackground(createScreenBackground());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(36), dp(24), dp(32));
        scrollView.addView(root);

        addAppIcon(root);
        addTitle(root);
        addStatus(root);
        addBatteryCard(root);
        addSetupCard(root);
        addButtons(root);
        addPrivacyNote(root);

        setContentView(scrollView);
        startEntranceAnimation(root);
        prepareGlowAnimation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
        updateBatteryCard();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pulseX != null) pulseX.cancel();
        if (pulseY != null) pulseY.cancel();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.parseColor("#07120C"));
        window.setNavigationBarColor(Color.parseColor("#050A07"));
    }

    private GradientDrawable createScreenBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.parseColor("#092014"),
                        Color.parseColor("#07140E"),
                        Color.parseColor("#050A07")
                }
        );
    }

    private void addAppIcon(LinearLayout root) {
        FrameLayout iconContainer = new FrameLayout(this);
        glowRing = new View(this);
        iconContainer.addView(glowRing,
                new FrameLayout.LayoutParams(dp(172), dp(172), Gravity.CENTER));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher_foreground);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setElevation(dp(10));
        iconContainer.addView(icon,
                new FrameLayout.LayoutParams(dp(124), dp(124), Gravity.CENTER));

        root.addView(iconContainer,
                new LinearLayout.LayoutParams(dp(180), dp(180)));
    }

    private void addTitle(LinearLayout root) {
        TextView title = createText("Block & Filter", 30, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(4));
        root.addView(title);

        TextView tagline = createText(
                "Reduce distraction. Keep control.", 14, TEXT_DIM, false);
        tagline.setGravity(Gravity.CENTER);
        root.addView(tagline);
    }

    private void addStatus(LinearLayout root) {
        statusPill = createText("", 13, Color.WHITE, true);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setLetterSpacing(0.10f);
        statusPill.setPadding(dp(18), dp(8), dp(18), dp(8));
        LinearLayout.LayoutParams statusParams = wrap();
        statusParams.topMargin = dp(20);
        root.addView(statusPill, statusParams);

        headline = createText("", 22, Color.WHITE, true);
        headline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headlineParams = wrap();
        headlineParams.topMargin = dp(20);
        root.addView(headline, headlineParams);

        subText = createText("", 14, TEXT_DIM, false);
        subText.setGravity(Gravity.CENTER);
        subText.setPadding(dp(8), dp(7), dp(8), 0);
        subText.setLineSpacing(0, 1.12f);
        root.addView(subText);
    }

    private void addBatteryCard(LinearLayout root) {
        batteryCard = new LinearLayout(this);
        batteryCard.setOrientation(LinearLayout.VERTICAL);
        batteryCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        batteryCard.addView(createText(
                "Keep protection running", 17, Color.WHITE, true));

        batteryStatusText = createText("", 14, TEXT_DIM, false);
        batteryStatusText.setPadding(0, dp(8), 0, dp(16));
        batteryStatusText.setLineSpacing(0, 1.15f);
        batteryCard.addView(batteryStatusText);

        batteryActionButton = createText("", 15, Color.WHITE, true);
        batteryActionButton.setGravity(Gravity.CENTER);
        batteryActionButton.setPadding(dp(16), dp(13), dp(16), dp(13));
        batteryActionButton.setOnClickListener(view -> {
            animatePress(view);
            requestBatteryExemption();
        });
        batteryCard.addView(batteryActionButton, match());

        LinearLayout.LayoutParams params = match();
        params.topMargin = dp(20);
        root.addView(batteryCard, params);
    }

    private boolean isBatteryOptimisationDisabled() {
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        return manager != null
                && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void updateBatteryCard() {
        if (batteryCard == null || batteryStatusText == null
                || batteryActionButton == null) return;

        if (isBatteryOptimisationDisabled()) {
            batteryStatusText.setText(
                    "Battery restrictions are disabled. Block & Filter can remain available while the device is idle.");
            batteryStatusText.setTextColor(GREEN);
            batteryActionButton.setText("Battery setting enabled");
            batteryActionButton.setEnabled(false);
            batteryActionButton.setAlpha(0.72f);
            batteryActionButton.setBackground(rounded(
                    withAlpha(GREEN, 55), dp(26), withAlpha(GREEN, 120)));
            batteryCard.setBackground(rounded(
                    withAlpha(GREEN, 24), dp(20), withAlpha(GREEN, 100)));
        } else {
            batteryStatusText.setText(
                    "Allow Block & Filter to run without battery restrictions. This can help Android keep protection available while the device is idle.");
            batteryStatusText.setTextColor(TEXT_DIM);
            batteryActionButton.setText("Disable battery optimisation");
            batteryActionButton.setEnabled(true);
            batteryActionButton.setAlpha(1f);

            GradientDrawable buttonBackground = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.parseColor("#00A857"), Color.parseColor("#007D40")});
            buttonBackground.setCornerRadius(dp(26));
            batteryActionButton.setBackground(buttonBackground);
            batteryCard.setBackground(rounded(
                    withAlpha(AMBER, 20), dp(20), withAlpha(AMBER, 125)));
        }
    }

    private void requestBatteryExemption() {
        if (isBatteryOptimisationDisabled()) {
            updateBatteryCard();
            return;
        }

        new AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Keep protection active")
                .setMessage(
                        "Android may restrict background activity while the device is idle.\n\n" +
                        "Continue to the Android system prompt and allow Block & Filter to run without battery optimisation.")
                .setPositiveButton("Continue",
                        (dialog, which) -> openBatteryExemptionRequest())
                .setNegativeButton("Not now", null)
                .show();
    }

    private void openBatteryExemptionRequest() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception ignored) {
            openBatterySettings();
        }
    }

    private void openBatterySettings() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception ignored) {
            openAppInfo();
        }
    }

    private void addSetupCard(LinearLayout root) {
        setupCard = new LinearLayout(this);
        setupCard.setOrientation(LinearLayout.VERTICAL);
        setupCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        setupCard.setBackground(rounded(
                CARD, dp(20), Color.parseColor("#24FFFFFF")));

        TextView heading = createText(
                "SETUP", 12, Color.parseColor("#99FFFFFF"), true);
        heading.setLetterSpacing(0.12f);
        setupCard.addView(heading);
        setupCard.addView(createStep(1, "Tap Turn On Protection"));
        setupCard.addView(createStep(2, "Open Installed apps / Downloaded apps"));
        setupCard.addView(createStep(3, "Select Block & Filter"));
        setupCard.addView(createStep(4, "Switch Accessibility ON"));
        setupCard.addView(createStep(5, "Return to Block & Filter"));

        LinearLayout.LayoutParams params = match();
        params.topMargin = dp(18);
        root.addView(setupCard, params);
    }

    private LinearLayout createStep(int number, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));

        TextView numberView = createText(
                String.valueOf(number), 13, Color.WHITE, true);
        numberView.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#00C96B"), Color.parseColor("#006C39")});
        circle.setShape(GradientDrawable.OVAL);
        numberView.setBackground(circle);
        row.addView(numberView,
                new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView description = createText(label, 15, Color.WHITE, false);
        description.setPadding(dp(14), 0, 0, 0);
        row.addView(description,
                new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void addButtons(LinearLayout root) {
        primaryButton = createText("", 17, Color.WHITE, true);
        primaryButton.setGravity(Gravity.CENTER);
        primaryButton.setPadding(dp(16), dp(17), dp(16), dp(17));
        GradientDrawable primaryBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#00BD63"), Color.parseColor("#00713D")});
        primaryBackground.setCornerRadius(dp(30));
        primaryButton.setBackground(primaryBackground);
        primaryButton.setElevation(dp(7));
        primaryButton.setOnClickListener(view -> {
            animatePress(view);
            if (isServiceEnabled()) openAccessibilitySettings();
            else showAccessibilityDisclosure();
        });
        LinearLayout.LayoutParams primaryParams = match();
        primaryParams.topMargin = dp(26);
        root.addView(primaryButton, primaryParams);

        restrictedButton = createText(
                "Accessibility switch unavailable?", 14, Color.WHITE, false);
        configureSecondaryButton(restrictedButton);
        restrictedButton.setOnClickListener(view -> {
            animatePress(view);
            showRestrictedSettingsHelp();
        });
        LinearLayout.LayoutParams restrictedParams = match();
        restrictedParams.topMargin = dp(12);
        root.addView(restrictedButton, restrictedParams);

        hideButton = createText("", 14, Color.WHITE, false);
        configureSecondaryButton(hideButton);
        hideButton.setOnClickListener(view -> {
            animatePress(view);
            handleHideIcon();
        });
        LinearLayout.LayoutParams hideParams = match();
        hideParams.topMargin = dp(12);
        root.addView(hideButton, hideParams);
    }

    private void configureSecondaryButton(TextView button) {
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), dp(14), dp(16), dp(14));
        button.setBackground(rounded(
                Color.TRANSPARENT, dp(30), Color.parseColor("#55FFFFFF")));
    }

    private void addPrivacyNote(LinearLayout root) {
        TextView privacy = createText(
                "Blocking logic runs locally on this device",
                12, Color.parseColor("#80FFFFFF"), false);
        privacy.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = wrap();
        params.topMargin = dp(22);
        root.addView(privacy, params);
    }

    private void updateUi() {
        boolean active = isServiceEnabled();
        updateGlow(active ? GREEN : RED);

        if (active) {
            statusPill.setText("●  PROTECTION ACTIVE");
            statusPill.setTextColor(GREEN);
            statusPill.setBackground(rounded(
                    withAlpha(GREEN, 40), dp(22), GREEN));
            headline.setText("Protection is active");
            subText.setText(
                    "Instagram Reels, YouTube Shorts and Facebook Reels\n" +
                    "are monitored by Block & Filter.");
            setupCard.setVisibility(View.GONE);
            primaryButton.setText("Open Accessibility Settings");
            restrictedButton.setVisibility(View.GONE);
        } else {
            statusPill.setText("●  PROTECTION OFF");
            statusPill.setTextColor(RED);
            statusPill.setBackground(rounded(
                    withAlpha(RED, 40), dp(22), RED));
            headline.setText("Protection is disabled");
            subText.setText(
                    "Enable the Accessibility Service to activate Block & Filter.");
            setupCard.setVisibility(View.VISIBLE);
            primaryButton.setText("Turn On Protection");
            restrictedButton.setVisibility(
                    Build.VERSION.SDK_INT >= 33 ? View.VISIBLE : View.GONE);
        }

        hideButton.setText(isLauncherHidden()
                ? "Show app icon" : "Hide app icon");
        startPulse();
    }

    private boolean isServiceEnabled() {
        ComponentName expected = new ComponentName(
                this, BlockerAccessibilityService.class);
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;

        TextUtils.SimpleStringSplitter splitter =
                new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            ComponentName enabled =
                    ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(enabled)) return true;
        }
        return false;
    }

    private void showAccessibilityDisclosure() {
        new AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Turn on Block & Filter")
                .setMessage(
                        "Block & Filter uses Android Accessibility to detect supported Reels and Shorts screens and return from blocked content.\n\n" +
                        "Blocking logic runs locally on this device.")
                .setPositiveButton("Continue",
                        (dialog, which) -> openAccessibilitySettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        String component = new ComponentName(
                this, BlockerAccessibilityService.class).flattenToString();
        Bundle args = new Bundle();
        args.putString(":settings:fragment_args_key", component);
        intent.putExtra(":settings:fragment_args_key", component);
        intent.putExtra(":settings:show_fragment_args", args);
        startActivity(intent);
    }

    private void showRestrictedSettingsHelp() {
        new AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Accessibility unavailable?")
                .setMessage(
                        "Android may restrict Accessibility for applications installed outside an app store.\n\n" +
                        "Open App Info and check whether Android provides an option to allow restricted settings.")
                .setPositiveButton("Open App Info",
                        (dialog, which) -> openAppInfo())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAppInfo() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private ComponentName getLauncherAlias() {
        return new ComponentName(
                this, getPackageName() + ".LauncherAlias");
    }

    private boolean isLauncherHidden() {
        int state = getPackageManager().getComponentEnabledSetting(
                getLauncherAlias());
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void handleHideIcon() {
        if (isLauncherHidden()) {
            setLauncherHidden(false);
            Toast.makeText(this,
                    "App icon enabled", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Hide app icon?")
                .setMessage(
                        "Block & Filter will be removed from the normal app launcher.\n\n" +
                        "The Accessibility Service can continue operating. Before hiding the icon, ensure Block & Filter can be reopened from Android Accessibility settings.")
                .setPositiveButton("Hide icon", (dialog, which) -> {
                    setLauncherHidden(true);
                    Toast.makeText(this,
                            "App icon hidden", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setLauncherHidden(boolean hidden) {
        int newState = hidden
                ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        getPackageManager().setComponentEnabledSetting(
                getLauncherAlias(),
                newState,
                PackageManager.DONT_KILL_APP);
        updateUi();
    }

    private void startEntranceAnimation(View root) {
        root.setAlpha(0f);
        root.setTranslationY(dp(25));
        root.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(500)
                .start();
    }

    private void prepareGlowAnimation() {
        pulseX = ObjectAnimator.ofFloat(
                glowRing, "scaleX", 0.92f, 1.08f);
        pulseY = ObjectAnimator.ofFloat(
                glowRing, "scaleY", 0.92f, 1.08f);

        for (ObjectAnimator animator : new ObjectAnimator[]{pulseX, pulseY}) {
            animator.setDuration(1200);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.REVERSE);
        }
    }

    private void startPulse() {
        if (pulseX != null && !pulseX.isRunning()) {
            pulseX.start();
            pulseY.start();
        }
    }

    private void updateGlow(int color) {
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.OVAL);
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientRadius(dp(85));
        glow.setColors(new int[]{
                withAlpha(color, 110),
                withAlpha(color, 36),
                Color.TRANSPARENT
        });
        glowRing.setBackground(glow);
    }

    private void animatePress(View view) {
        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(70)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();
    }

    private TextView createText(
            String value,
            int size,
            int color,
            boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        text.setTypeface(Typeface.create(
                bold ? "sans-serif-medium" : "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        return text;
    }

    private GradientDrawable rounded(
            int fillColor,
            int radius,
            int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value
                * getResources().getDisplayMetrics().density);
    }
}

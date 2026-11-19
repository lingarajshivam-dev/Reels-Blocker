package com.android.filtersettings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int RED =
            Color.parseColor("#FF3B5C");

    private static final int GREEN =
            Color.parseColor("#00D98B");

    private static final int AMBER =
            Color.parseColor("#FFB020");

    private static final int TEXT_DIM =
            Color.parseColor("#B3FFFFFF");

    private static final int CARD =
            Color.parseColor("#16FFFFFF");

    private TextView statusPill;
    private TextView headline;
    private TextView subText;

    private LinearLayout setupCard;

    private LinearLayout batteryCard;
    private TextView batteryStatusText;
    private TextView batteryActionButton;

    private TextView primaryButton;
    private TextView restrictedButton;
    private TextView deviceAdminButton;
    private TextView hideButton;

    // =========================================================
    // ACTIVITY
    // =========================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        configureWindow();

        ScrollView scrollView =
                new ScrollView(this);

        scrollView.setFillViewport(true);

        scrollView.setBackground(
                createScreenBackground()
        );

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setGravity(
                Gravity.CENTER_HORIZONTAL
        );

        /*
         * Smaller top padding because the large in-app
         * launcher icon has intentionally been removed.
         */
        root.setPadding(
                dp(24),
                dp(42),
                dp(24),
                dp(32)
        );

        scrollView.addView(root);

        /*
         * No addAppIcon(root).
         *
         * The application starts directly with the title.
         */
        addTitle(root);
        addStatus(root);
        addBatteryCard(root);
        addSetupCard(root);
        addButtons(root);
        addPrivacyNote(root);

        setContentView(scrollView);

        startEntranceAnimation(root);
    }

    @Override
    protected void onResume() {

        super.onResume();

        updateUi();
        updateBatteryCard();
    }

    // =========================================================
    // WINDOW
    // =========================================================

    private void configureWindow() {

        Window window =
                getWindow();

        window.setStatusBarColor(
                Color.parseColor("#07120C")
        );

        window.setNavigationBarColor(
                Color.parseColor("#050A07")
        );
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

    // =========================================================
    // TITLE
    // =========================================================

    private void addTitle(
            LinearLayout root) {

        TextView title =
                createText(
                        "Block & Filter",
                        30,
                        Color.WHITE,
                        true
                );

        title.setGravity(
                Gravity.CENTER
        );

        root.addView(title);

        TextView tagline =
                createText(
                        "Reduce distraction. Keep control.",
                        14,
                        TEXT_DIM,
                        false
                );

        tagline.setGravity(
                Gravity.CENTER
        );

        tagline.setPadding(
                0,
                dp(6),
                0,
                0
        );

        root.addView(tagline);
    }

    // =========================================================
    // STATUS
    // =========================================================

    private void addStatus(
            LinearLayout root) {

        statusPill =
                createText(
                        "",
                        13,
                        Color.WHITE,
                        true
                );

        statusPill.setGravity(
                Gravity.CENTER
        );

        statusPill.setLetterSpacing(
                0.08f
        );

        statusPill.setPadding(
                dp(18),
                dp(8),
                dp(18),
                dp(8)
        );

        LinearLayout.LayoutParams statusParams =
                wrap();

        statusParams.topMargin =
                dp(26);

        root.addView(
                statusPill,
                statusParams
        );

        headline =
                createText(
                        "",
                        22,
                        Color.WHITE,
                        true
                );

        headline.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams headlineParams =
                wrap();

        headlineParams.topMargin =
                dp(20);

        root.addView(
                headline,
                headlineParams
        );

        subText =
                createText(
                        "",
                        14,
                        TEXT_DIM,
                        false
                );

        subText.setGravity(
                Gravity.CENTER
        );

        subText.setPadding(
                dp(8),
                dp(7),
                dp(8),
                0
        );

        subText.setLineSpacing(
                0,
                1.12f
        );

        root.addView(subText);
    }

    // =========================================================
    // BATTERY
    // =========================================================

    private void addBatteryCard(
            LinearLayout root) {

        batteryCard =
                new LinearLayout(this);

        batteryCard.setOrientation(
                LinearLayout.VERTICAL
        );

        batteryCard.setPadding(
                dp(20),
                dp(18),
                dp(20),
                dp(18)
        );

        TextView title =
                createText(
                        "Keep protection running",
                        17,
                        Color.WHITE,
                        true
                );

        batteryCard.addView(title);

        batteryStatusText =
                createText(
                        "",
                        14,
                        TEXT_DIM,
                        false
                );

        batteryStatusText.setPadding(
                0,
                dp(8),
                0,
                dp(16)
        );

        batteryStatusText.setLineSpacing(
                0,
                1.15f
        );

        batteryCard.addView(
                batteryStatusText
        );

        batteryActionButton =
                createText(
                        "",
                        15,
                        Color.WHITE,
                        true
                );

        configureButton(
                batteryActionButton
        );

        batteryActionButton.setOnClickListener(
                view -> {

                    animatePress(view);
                    requestBatteryExemption();
                }
        );

        batteryCard.addView(
                batteryActionButton,
                match()
        );

        LinearLayout.LayoutParams params =
                match();

        params.topMargin =
                dp(24);

        root.addView(
                batteryCard,
                params
        );
    }

    private boolean isBatteryOptimisationDisabled() {

        PowerManager manager =
                (PowerManager)
                        getSystemService(
                                POWER_SERVICE
                        );

        return manager != null
                && manager.isIgnoringBatteryOptimizations(
                        getPackageName()
                );
    }

    private void updateBatteryCard() {

        if (batteryCard == null
                || batteryStatusText == null
                || batteryActionButton == null) {

            return;
        }

        boolean unrestricted =
                isBatteryOptimisationDisabled();

        if (unrestricted) {

            batteryStatusText.setText(
                    "Battery restrictions are disabled. " +
                    "Block & Filter can remain available while the device is idle."
            );

            batteryStatusText.setTextColor(
                    GREEN
            );

            batteryActionButton.setText(
                    "Battery setting enabled"
            );

            batteryActionButton.setEnabled(
                    false
            );

            batteryActionButton.setAlpha(
                    0.72f
            );

            batteryActionButton.setBackground(
                    rounded(
                            withAlpha(
                                    GREEN,
                                    55
                            ),
                            dp(26),
                            withAlpha(
                                    GREEN,
                                    120
                            )
                    )
            );

            batteryCard.setBackground(
                    rounded(
                            withAlpha(
                                    GREEN,
                                    24
                            ),
                            dp(20),
                            withAlpha(
                                    GREEN,
                                    100
                            )
                    )
            );

        } else {

            batteryStatusText.setText(
                    "Allow Block & Filter to run without battery restrictions. " +
                    "This can help Android keep protection available while the device is idle."
            );

            batteryStatusText.setTextColor(
                    TEXT_DIM
            );

            batteryActionButton.setText(
                    "Disable battery optimisation"
            );

            batteryActionButton.setEnabled(
                    true
            );

            batteryActionButton.setAlpha(
                    1f
            );

            GradientDrawable background =
                    new GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            new int[]{
                                    Color.parseColor("#00A857"),
                                    Color.parseColor("#007D40")
                            }
                    );

            background.setCornerRadius(
                    dp(26)
            );

            batteryActionButton.setBackground(
                    background
            );

            batteryCard.setBackground(
                    rounded(
                            withAlpha(
                                    AMBER,
                                    20
                            ),
                            dp(20),
                            withAlpha(
                                    AMBER,
                                    125
                            )
                    )
            );
        }
    }

    private void requestBatteryExemption() {

        if (isBatteryOptimisationDisabled()) {
            return;
        }

        try {

            Intent intent =
                    new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    );

            intent.setData(
                    Uri.parse(
                            "package:"
                                    + getPackageName()
                    )
            );

            startActivity(intent);

        } catch (Exception ignored) {

            try {

                startActivity(
                        new Intent(
                                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        )
                );

            } catch (Exception ignoredAgain) {

                openAppInfo();
            }
        }
    }

    // =========================================================
    // SETUP CARD
    // =========================================================

    private void addSetupCard(
            LinearLayout root) {

        setupCard =
                new LinearLayout(this);

        setupCard.setOrientation(
                LinearLayout.VERTICAL
        );

        setupCard.setPadding(
                dp(20),
                dp(18),
                dp(20),
                dp(18)
        );

        setupCard.setBackground(
                rounded(
                        CARD,
                        dp(20),
                        Color.parseColor("#24FFFFFF")
                )
        );

        TextView heading =
                createText(
                        "SETUP",
                        12,
                        Color.parseColor("#99FFFFFF"),
                        true
                );

        heading.setLetterSpacing(
                0.12f
        );

        setupCard.addView(heading);

        setupCard.addView(
                createStep(
                        1,
                        "Turn On Protection"
                )
        );

        setupCard.addView(
                createStep(
                        2,
                        "Enable Accessibility"
                )
        );

        setupCard.addView(
                createStep(
                        3,
                        "Enable Parental Protection"
                )
        );

        setupCard.addView(
                createStep(
                        4,
                        "Return to Block & Filter"
                )
        );

        LinearLayout.LayoutParams params =
                match();

        params.topMargin =
                dp(18);

        root.addView(
                setupCard,
                params
        );
    }

    private LinearLayout createStep(
            int number,
            String label) {

        LinearLayout row =
                new LinearLayout(this);

        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        row.setGravity(
                Gravity.CENTER_VERTICAL
        );

        row.setPadding(
                0,
                dp(9),
                0,
                dp(9)
        );

        TextView numberView =
                createText(
                        String.valueOf(number),
                        13,
                        Color.WHITE,
                        true
                );

        numberView.setGravity(
                Gravity.CENTER
        );

        GradientDrawable circle =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{
                                Color.parseColor("#00C96B"),
                                Color.parseColor("#006C39")
                        }
                );

        circle.setShape(
                GradientDrawable.OVAL
        );

        numberView.setBackground(
                circle
        );

        row.addView(
                numberView,
                new LinearLayout.LayoutParams(
                        dp(30),
                        dp(30)
                )
        );

        TextView description =
                createText(
                        label,
                        15,
                        Color.WHITE,
                        false
                );

        description.setPadding(
                dp(14),
                0,
                0,
                0
        );

        row.addView(
                description,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        return row;
    }

    // =========================================================
    // BUTTONS
    // =========================================================

    private void addButtons(
            LinearLayout root) {

        primaryButton =
                createText(
                        "",
                        17,
                        Color.WHITE,
                        true
                );

        configureButton(
                primaryButton
        );

        GradientDrawable mainBackground =
                new GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        new int[]{
                                Color.parseColor("#00BD63"),
                                Color.parseColor("#00713D")
                        }
                );

        mainBackground.setCornerRadius(
                dp(30)
        );

        primaryButton.setBackground(
                mainBackground
        );

        primaryButton.setElevation(
                dp(7)
        );

        primaryButton.setOnClickListener(
                view -> {

                    animatePress(view);

                    if (!isServiceEnabled()) {

                        showAccessibilityDisclosure();

                    } else if (!isDeviceAdminActive()) {

                        requestDeviceAdmin();

                    } else {

                        openAccessibilitySettings();
                    }
                }
        );

        LinearLayout.LayoutParams primaryParams =
                match();

        primaryParams.topMargin =
                dp(26);

        root.addView(
                primaryButton,
                primaryParams
        );

        restrictedButton =
                createText(
                        "Accessibility switch unavailable?",
                        14,
                        Color.WHITE,
                        false
                );

        configureSecondaryButton(
                restrictedButton
        );

        restrictedButton.setOnClickListener(
                view -> {

                    animatePress(view);
                    showRestrictedSettingsHelp();
                }
        );

        LinearLayout.LayoutParams restrictedParams =
                match();

        restrictedParams.topMargin =
                dp(12);

        root.addView(
                restrictedButton,
                restrictedParams
        );

        /*
         * One-tap Device Admin activation button.
         */
        deviceAdminButton =
                createText(
                        "",
                        14,
                        Color.WHITE,
                        true
                );

        configureButton(
                deviceAdminButton
        );

        deviceAdminButton.setOnClickListener(
                view -> {

                    animatePress(view);
                    requestDeviceAdmin();
                }
        );

        LinearLayout.LayoutParams adminParams =
                match();

        adminParams.topMargin =
                dp(12);

        root.addView(
                deviceAdminButton,
                adminParams
        );

        /*
         * Hide launcher icon button.
         */
        hideButton =
                createText(
                        "",
                        14,
                        Color.WHITE,
                        false
                );

        configureSecondaryButton(
                hideButton
        );

        hideButton.setOnClickListener(
                view -> {

                    animatePress(view);
                    handleHideIcon();
                }
        );

        LinearLayout.LayoutParams hideParams =
                match();

        hideParams.topMargin =
                dp(12);

        root.addView(
                hideButton,
                hideParams
        );
    }

    // =========================================================
    // DEVICE ADMIN
    // =========================================================

    private ComponentName getAdminComponent() {

        return new ComponentName(
                this,
                BlockFilterAdminReceiver.class
        );
    }

    private boolean isDeviceAdminActive() {

        DevicePolicyManager manager =
                (DevicePolicyManager)
                        getSystemService(
                                DEVICE_POLICY_SERVICE
                        );

        return manager != null
                && manager.isAdminActive(
                        getAdminComponent()
                );
    }

    private void requestDeviceAdmin() {

        if (isDeviceAdminActive()) {

            Toast.makeText(
                    this,
                    "Parental protection is already active",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Intent intent =
                new Intent(
                        DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
                );

        intent.putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                getAdminComponent()
        );

        intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Activate Block & Filter for parental protection."
        );

        startActivity(intent);
    }

    private void updateDeviceAdminButton() {

        if (deviceAdminButton == null) {
            return;
        }

        if (isDeviceAdminActive()) {

            deviceAdminButton.setText(
                    "Parental Protection  ✓ Active"
            );

            deviceAdminButton.setTextColor(
                    GREEN
            );

            deviceAdminButton.setBackground(
                    rounded(
                            withAlpha(
                                    GREEN,
                                    28
                            ),
                            dp(30),
                            withAlpha(
                                    GREEN,
                                    150
                            )
                    )
            );

        } else {

            deviceAdminButton.setText(
                    "Enable Parental Protection"
            );

            deviceAdminButton.setTextColor(
                    Color.WHITE
            );

            GradientDrawable background =
                    new GradientDrawable(
                            GradientDrawable.Orientation.LEFT_RIGHT,
                            new int[]{
                                    Color.parseColor("#00A857"),
                                    Color.parseColor("#006C39")
                            }
                    );

            background.setCornerRadius(
                    dp(30)
            );

            deviceAdminButton.setBackground(
                    background
            );
        }
    }

    // =========================================================
    // ACCESSIBILITY
    // =========================================================

    private boolean isServiceEnabled() {

        ComponentName expected =
                new ComponentName(
                        this,
                        BlockerAccessibilityService.class
                );

        String enabledServices =
                Settings.Secure.getString(
                        getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                );

        if (enabledServices == null) {
            return false;
        }

        TextUtils.SimpleStringSplitter splitter =
                new TextUtils.SimpleStringSplitter(
                        ':'
                );

        splitter.setString(
                enabledServices
        );

        while (splitter.hasNext()) {

            ComponentName enabled =
                    ComponentName.unflattenFromString(
                            splitter.next()
                    );

            if (expected.equals(enabled)) {
                return true;
            }
        }

        return false;
    }

    private void showAccessibilityDisclosure() {

        new AlertDialog.Builder(
                this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert
        )
                .setTitle(
                        "Turn on Block & Filter"
                )
                .setMessage(
                        "Block & Filter uses Android Accessibility to detect " +
                        "supported Reels and Shorts screens and return from blocked content.\n\n" +
                        "Blocking logic runs locally on this device."
                )
                .setPositiveButton(
                        "Continue",
                        (dialog, which) ->
                                openAccessibilitySettings()
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void openAccessibilitySettings() {

        Intent intent =
                new Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS
                );

        String component =
                new ComponentName(
                        this,
                        BlockerAccessibilityService.class
                ).flattenToString();

        Bundle args =
                new Bundle();

        args.putString(
                ":settings:fragment_args_key",
                component
        );

        intent.putExtra(
                ":settings:fragment_args_key",
                component
        );

        intent.putExtra(
                ":settings:show_fragment_args",
                args
        );

        startActivity(intent);
    }

    // =========================================================
    // UI STATUS
    // =========================================================

    private void updateUi() {

        boolean accessibilityActive =
                isServiceEnabled();

        boolean adminActive =
                isDeviceAdminActive();

        updateDeviceAdminButton();

        if (!accessibilityActive) {

            setStatusStyle(
                    RED
            );

            statusPill.setText(
                    "●  PROTECTION OFF"
            );

            headline.setText(
                    "Accessibility required"
            );

            subText.setText(
                    "Enable Accessibility to activate short-video blocking."
            );

            setupCard.setVisibility(
                    View.VISIBLE
            );

            primaryButton.setText(
                    "Turn On Protection"
            );

            restrictedButton.setVisibility(
                    Build.VERSION.SDK_INT >= 33
                            ? View.VISIBLE
                            : View.GONE
            );

        } else if (!adminActive) {

            setStatusStyle(
                    AMBER
            );

            statusPill.setText(
                    "●  SETUP INCOMPLETE"
            );

            headline.setText(
                    "Parental protection required"
            );

            subText.setText(
                    "Content blocking is active. Enable Device Admin for parental protection."
            );

            setupCard.setVisibility(
                    View.GONE
            );

            primaryButton.setText(
                    "Enable Parental Protection"
            );

            restrictedButton.setVisibility(
                    View.GONE
            );

        } else {

            setStatusStyle(
                    GREEN
            );

            statusPill.setText(
                    "●  PROTECTION ACTIVE"
            );

            headline.setText(
                    "Protection is active"
            );

            subText.setText(
                    "Instagram Reels, YouTube Shorts and Facebook Reels\n" +
                    "are monitored by Block & Filter."
            );

            setupCard.setVisibility(
                    View.GONE
            );

            primaryButton.setText(
                    "Open Accessibility Settings"
            );

            restrictedButton.setVisibility(
                    View.GONE
            );
        }

        hideButton.setText(
                isLauncherHidden()
                        ? "Show app icon"
                        : "Hide app icon"
        );
    }

    private void setStatusStyle(
            int color) {

        statusPill.setTextColor(
                color
        );

        statusPill.setBackground(
                rounded(
                        withAlpha(
                                color,
                                40
                        ),
                        dp(22),
                        color
                )
        );
    }

    // =========================================================
    // HIDE / SHOW LAUNCHER ICON
    // =========================================================

    private ComponentName getLauncherAlias() {

        return new ComponentName(
                getPackageName(),
                getPackageName()
                        + ".LauncherAlias"
        );
    }

    private boolean isLauncherHidden() {

        int state =
                getPackageManager()
                        .getComponentEnabledSetting(
                                getLauncherAlias()
                        );

        return state
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                || state
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
    }

    private void handleHideIcon() {

        if (isLauncherHidden()) {

            setLauncherHidden(
                    false
            );

            return;
        }

        new AlertDialog.Builder(
                this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert
        )
                .setTitle(
                        "Hide app icon?"
                )
                .setMessage(
                        "Block & Filter will be removed from the app launcher.\n\n" +
                        "Protection will continue running. Block & Filter can still " +
                        "be accessed through Android Accessibility settings."
                )
                .setPositiveButton(
                        "Hide icon",
                        (dialog, which) ->
                                setLauncherHidden(true)
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void setLauncherHidden(
            boolean hidden) {

        int newState =
                hidden
                        ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

        getPackageManager()
                .setComponentEnabledSetting(
                        getLauncherAlias(),
                        newState,
                        PackageManager.DONT_KILL_APP
                );

        if (hidden) {

            Toast.makeText(
                    this,
                    "App icon hidden. Launcher may take a few seconds to refresh.",
                    Toast.LENGTH_LONG
            ).show();

        } else {

            Toast.makeText(
                    this,
                    "App icon enabled",
                    Toast.LENGTH_SHORT
            ).show();
        }

        updateUi();
    }

    // =========================================================
    // RESTRICTED SETTINGS / APP INFO
    // =========================================================

    private void showRestrictedSettingsHelp() {

        new AlertDialog.Builder(
                this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert
        )
                .setTitle(
                        "Accessibility unavailable?"
                )
                .setMessage(
                        "Open App Info and check whether Android provides " +
                        "an option to allow restricted settings."
                )
                .setPositiveButton(
                        "Open App Info",
                        (dialog, which) ->
                                openAppInfo()
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void openAppInfo() {

        Intent intent =
                new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                );

        intent.setData(
                Uri.parse(
                        "package:"
                                + getPackageName()
                )
        );

        startActivity(intent);
    }

    // =========================================================
    // PRIVACY NOTE
    // =========================================================

    private void addPrivacyNote(
            LinearLayout root) {

        TextView privacy =
                createText(
                        "Blocking logic runs locally on this device",
                        12,
                        Color.parseColor("#80FFFFFF"),
                        false
                );

        privacy.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams params =
                wrap();

        params.topMargin =
                dp(22);

        root.addView(
                privacy,
                params
        );
    }

    // =========================================================
    // ANIMATION
    // =========================================================

    private void startEntranceAnimation(
            View root) {

        root.setAlpha(
                0f
        );

        root.setTranslationY(
                dp(25)
        );

        root.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(500)
                .start();
    }

    private void animatePress(
            View view) {

        view.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(70)
                .withEndAction(
                        () ->
                                view.animate()
                                        .scaleX(1f)
                                        .scaleY(1f)
                                        .setDuration(100)
                                        .start()
                )
                .start();
    }

    // =========================================================
    // UI HELPERS
    // =========================================================

    private void configureButton(
            TextView button) {

        button.setGravity(
                Gravity.CENTER
        );

        button.setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
        );
    }

    private void configureSecondaryButton(
            TextView button) {

        configureButton(
                button
        );

        button.setBackground(
                rounded(
                        Color.TRANSPARENT,
                        dp(30),
                        Color.parseColor("#55FFFFFF")
                )
        );
    }

    private TextView createText(
            String value,
            int size,
            int color,
            boolean bold) {

        TextView text =
                new TextView(this);

        text.setText(
                value
        );

        text.setTextSize(
                size
        );

        text.setTextColor(
                color
        );

        text.setTypeface(
                Typeface.create(
                        bold
                                ? "sans-serif-medium"
                                : "sans-serif",
                        bold
                                ? Typeface.BOLD
                                : Typeface.NORMAL
                )
        );

        return text;
    }

    private GradientDrawable rounded(
            int fillColor,
            int radius,
            int strokeColor) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(
                fillColor
        );

        drawable.setCornerRadius(
                radius
        );

        drawable.setStroke(
                dp(1),
                strokeColor
        );

        return drawable;
    }

    private int withAlpha(
            int color,
            int alpha) {

        return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    private LinearLayout.LayoutParams match() {

        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrap() {

        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(
            int value) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}

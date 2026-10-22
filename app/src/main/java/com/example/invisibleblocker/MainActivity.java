package com.example.invisibleblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusText;
    private TextView stepsText;
    private Button enableButton;
    private Button restrictedButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(pad, dp(48), pad, pad);
        root.setBackgroundColor(Color.parseColor("#1E1E2E"));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher);
        root.addView(icon, new LinearLayout.LayoutParams(dp(110), dp(110)));

        TextView title = new TextView(this);
        title.setText("Reels Blocker");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(16), 0, dp(8));
        root.addView(title);

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setPadding(0, dp(8), 0, dp(16));
        root.addView(statusText);

        stepsText = new TextView(this);
        stepsText.setTextColor(Color.parseColor("#DDDDDD"));
        stepsText.setTextSize(15);
        stepsText.setLineSpacing(0, 1.3f);
        root.addView(stepsText);

        enableButton = new Button(this);
        enableButton.setText("Turn On Accessibility");
        enableButton.setOnClickListener(v -> showDisclosure());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        root.addView(enableButton, lp);

        restrictedButton = new Button(this);
        restrictedButton.setText("Option greyed out? Tap here");
        restrictedButton.setOnClickListener(v -> openAppInfo());
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = dp(8);
        root.addView(restrictedButton, lp2);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi(); // re-check every time user comes back from Settings
    }

    private void updateUi() {
        if (isServiceEnabled()) {
            statusText.setText("✅ Protection is ON");
            statusText.setTextColor(Color.parseColor("#4CAF50"));
            stepsText.setText("Reels are now being blocked.\nYou can close this app.");
            enableButton.setText("Open Accessibility Settings");
            restrictedButton.setVisibility(Button.GONE);
        } else {
            statusText.setText("❌ Protection is OFF");
            statusText.setTextColor(Color.parseColor("#E53935"));
            stepsText.setText(
                    "To start blocking reels:\n\n" +
                    "1. Tap 'Turn On Accessibility' below\n" +
                    "2. Tap 'Installed apps' (or 'Downloaded apps')\n" +
                    "3. Tap 'Invisible Blocker'\n" +
                    "4. Switch it ON and tap 'Allow'\n" +
                    "5. Press Back to return here");
            enableButton.setText("Turn On Accessibility");
            restrictedButton.setVisibility(
                    Build.VERSION.SDK_INT >= 33 ? Button.VISIBLE : Button.GONE);
        }
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

    private void showDisclosure() {
        if (isServiceEnabled()) { openAccessibilitySettings(); return; }
        new AlertDialog.Builder(this)
                .setTitle("Accessibility permission needed")
                .setMessage("Reels Blocker uses the Accessibility Service only to detect when a " +
                        "Reels/Shorts screen opens and close it.\n\n" +
                        "It does not read, store or share your personal data.")
                .setPositiveButton("Continue", (d, w) -> openAccessibilitySettings())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        // Highlights our app in the list on many phones (ignored where unsupported)
        String component = new ComponentName(this, BlockerAccessibilityService.class).flattenToString();
        Bundle args = new Bundle();
        args.putString(":settings:fragment_args_key", component);
        intent.putExtra(":settings:fragment_args_key", component);
        intent.putExtra(":settings:show_fragment_args", args);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void openAppInfo() {
        new AlertDialog.Builder(this)
                .setTitle("Allow restricted setting")
                .setMessage("On Android 13+, apps installed from an APK are restricted.\n\n" +
                        "1. On the next screen tap ⋮ (top-right)\n" +
                        "2. Tap 'Allow restricted settings'\n" +
                        "3. Come back and tap 'Turn On Accessibility'")
                .setPositiveButton("Open App Info", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}

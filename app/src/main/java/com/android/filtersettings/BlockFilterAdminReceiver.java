package com.android.filtersettings;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class BlockFilterAdminReceiver extends DeviceAdminReceiver {

    @Override
    public void onEnabled(
            Context context,
            Intent intent) {

        super.onEnabled(context, intent);

        applyParentalProtection(context);

        Toast.makeText(
                context,
                "Parental protection enabled",
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    public void onProfileProvisioningComplete(
            Context context,
            Intent intent) {

        super.onProfileProvisioningComplete(
                context,
                intent
        );

        applyParentalProtection(context);
    }

    private void applyParentalProtection(
            Context context) {

        DevicePolicyManager manager =
                (DevicePolicyManager)
                        context.getSystemService(
                                Context.DEVICE_POLICY_SERVICE
                        );

        if (manager == null
                || !manager.isDeviceOwnerApp(
                        context.getPackageName()
                )) {

            return;
        }

        ComponentName admin =
                new ComponentName(
                        context,
                        BlockFilterAdminReceiver.class
                );

        manager.setUninstallBlocked(
                admin,
                context.getPackageName(),
                true
        );

        manager.setShortSupportMessage(
                admin,
                "Restricted by parental controls"
        );

        manager.setLongSupportMessage(
                admin,
                "Block & Filter is protected by the device administrator. " +
                "Parental authorization is required to remove protection."
        );
    }
}

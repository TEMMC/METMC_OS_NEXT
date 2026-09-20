package com.metmc.os.next;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import java.io.File;

public class UpdateInstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String path=intent.getStringExtra("metmc_apk_path");
        if(status==PackageInstaller.STATUS_SUCCESS && path!=null) {
            try { new File(path).delete(); } catch(Exception ignored) {}
            Intent launch=context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if(launch!=null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(launch);
            }
        }
    }
}
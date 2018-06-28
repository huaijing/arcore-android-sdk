package com.google.ar.core.examples.java.computervision.utility;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

/**
 * Created by jinghuai on 2018/6/28.
 */

public class WritePermissionHelper {
    private static final int WRITE_EXTERNAL_STORAGE_PERMISSION_CODE = 0;
    private static final String WRITE_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;


    /** Check to see we have the necessary permissions for this app. */
    public static boolean hasWritePermission(Activity activity) {
        return  ContextCompat.checkSelfPermission(activity, WRITE_EXTERNAL_STORAGE_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Check to see we have the necessary permissions for this app, and ask for them if we don't. */
    public static void requestWritePermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity, new String[] {WRITE_EXTERNAL_STORAGE_PERMISSION}, WRITE_EXTERNAL_STORAGE_PERMISSION_CODE);
    }


    /** Check to see if we need to show the rationale for this permission. */
    public static boolean shouldShowRequestPermissionRationale(Activity activity) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity,
                WRITE_EXTERNAL_STORAGE_PERMISSION);
    }

    /** Launch Application Setting to grant permission. */
    public static void launchPermissionSettings(Activity activity) {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
        activity.startActivity(intent);
    }
}

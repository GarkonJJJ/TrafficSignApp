package com.example.trafficsign.util;

import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.Manifest;

public class PermissionUtils {
    public static boolean hasCameraPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }
}

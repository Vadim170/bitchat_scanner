package com.vadim170.bitchatscanner.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 */
object PermissionUtils {
    
    /**
     */
    fun getRequiredPermissions(): List<String> {
        val list = mutableListOf<String>()
        

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
        } else {

            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        

        if (!list.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        
        return list
    }
    
    /**
     */
    fun getOptionalPermissions(): List<String> {
        val opt = mutableListOf<String>()
        

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            opt += Manifest.permission.POST_NOTIFICATIONS
        }
        
        return opt
    }
    
    /**
     */
    fun getAllPermissionsToRequest(): Array<String> {
        return (getRequiredPermissions() + getOptionalPermissions()).distinct().toTypedArray()
    }
    
    /**
     * 
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

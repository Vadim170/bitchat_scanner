package com.vadim170.bitchatscanner.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Утилиты для работы с разрешениями приложения
 */
object PermissionUtils {
    
    /**
     * Возвращает список обязательных разрешений в зависимости от версии Android
     */
    fun getRequiredPermissions(): List<String> {
        val list = mutableListOf<String>()
        
        // Разрешения Bluetooth для Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            // Для старых версий нужно разрешение на локацию для BLE
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        
        // ACCESS_FINE_LOCATION нужна в любом случае для GPS-меток
        if (!list.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
            list += Manifest.permission.ACCESS_FINE_LOCATION
        }
        
        return list
    }
    
    /**
     * Возвращает список опциональных разрешений
     */
    fun getOptionalPermissions(): List<String> {
        val opt = mutableListOf<String>()
        
        // Разрешение на уведомления для Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            opt += Manifest.permission.POST_NOTIFICATIONS
        }
        
        return opt
    }
    
    /**
     * Возвращает все разрешения для запроса (обязательные + опциональные)
     */
    fun getAllPermissionsToRequest(): Array<String> {
        return (getRequiredPermissions() + getOptionalPermissions()).distinct().toTypedArray()
    }
    
    /**
     * Проверяет, что все обязательные разрешения предоставлены
     * 
     * @param context Контекст приложения
     * @return true если все обязательные разрешения предоставлены
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}

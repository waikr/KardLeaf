package com.kangle.kardleaf

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.kangle.kardleaf.data.utils.KardLeafLog

object AppIconManager {
    private const val PREFS_NAME = "kardleaf_prefs"
    private const val KEY_APP_ICON = "app_icon"
    private const val LOG_TAG = "KardLeafAppIcon"

    enum class AppIcon(
        val label: String,
        val componentSuffix: String,
        @DrawableRes val iconResId: Int,
    ) {
        DEFAULT("默认图标", ".LauncherDefault", R.mipmap.ic_app_icon_default),
        LEAF("绿叶图标", ".LauncherLeaf", R.mipmap.ic_app_icon_leaf),
        MINIMAL("极简图标", ".LauncherMinimal", R.mipmap.ic_app_icon_minimal),
    }

    fun current(context: Context): AppIcon {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_APP_ICON, AppIcon.DEFAULT.name)
        return runCatching { AppIcon.valueOf(name ?: AppIcon.DEFAULT.name) }
            .getOrDefault(AppIcon.DEFAULT)
    }

    fun apply(context: Context, icon: AppIcon) {
        val packageManager = context.packageManager
        val aliasPackage = AppIconManager::class.java.`package`?.name ?: context.packageName
        AppIcon.values().forEach { item ->
            val state = if (item == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val componentName = ComponentName(context.packageName, aliasPackage + item.componentSuffix)
            runCatching {
                packageManager.setComponentEnabledSetting(
                    componentName,
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }.onFailure { error ->
                KardLeafLog.e(LOG_TAG, "apply icon component failed component=$componentName state=$state", error)
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_ICON, icon.name)
            .apply()
    }
}

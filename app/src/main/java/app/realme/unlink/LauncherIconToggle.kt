package app.realme.unlink

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

internal object LauncherIconToggle {
    const val ALIAS_CLASS_NAME = "app.realme.unlink.LauncherAlias"

    fun componentName(packageName: String): ComponentName =
        ComponentName(packageName, ALIAS_CLASS_NAME)

    fun isEnabled(state: Int): Boolean =
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun targetState(enabled: Boolean): Int = if (enabled) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun isEnabled(context: Context): Boolean = isEnabled(
        context.packageManager.getComponentEnabledSetting(componentName(context.packageName)),
    )

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            componentName(context.packageName),
            targetState(enabled),
            PackageManager.DONT_KILL_APP,
        )
    }
}

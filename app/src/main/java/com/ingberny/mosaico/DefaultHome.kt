package com.ingberny.mosaico

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Todo lo necesario para que Mosaico sea la pantalla de inicio predeterminada. */
object DefaultHome {
    const val REQ_ROLE = 41

    fun isDefault(ctx: Context): Boolean {
        val rm = ctx.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName == ctx.packageName
    }

    /** Abre el diálogo oficial de Android "¿Usar Mosaico como app de inicio?". */
    fun request(act: Activity) {
        val rm = act.getSystemService(RoleManager::class.java)
        try {
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                @Suppress("DEPRECATION")
                act.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE)
                return
            }
        } catch (e: Exception) { }
        openSettings(act)
    }

    /** Si el diálogo no aparece (algunas marcas lo bloquean), se abre la pantalla de ajustes. */
    fun openSettings(act: Activity) {
        try { act.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) {
            try { act.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) { }
        }
    }

    fun onResult(act: Activity, requestCode: Int) {
        if (requestCode == REQ_ROLE && !isDefault(act)) openSettings(act)
    }
}

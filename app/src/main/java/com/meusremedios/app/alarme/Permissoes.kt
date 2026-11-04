package com.meusremedios.app.alarme

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Verifica e abre os ajustes do sistema de que o alarme depende. */
object Permissoes {
    val ehXiaomi: Boolean = listOf("xiaomi", "redmi", "poco").any {
        Build.MANUFACTURER.equals(it, ignoreCase = true) || Build.BRAND.equals(it, ignoreCase = true)
    }

    fun notificacoes(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun telaCheia(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun alarmesExatos(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun bateriaLiberada(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun tudoCerto(context: Context): Boolean =
        notificacoes(context) && telaCheia(context) && alarmesExatos(context) && bateriaLiberada(context)

    fun abrirNotificacoes(context: Context) = abrir(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    fun abrirTelaCheia(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            abrir(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pacote(context)))
        } else {
            abrirDetalhes(context)
        }
    }

    fun abrirAlarmesExatos(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            abrir(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pacote(context)))
        } else {
            abrirDetalhes(context)
        }
    }

    @SuppressLint("BatteryLife") // app de uso pessoal, fora da Play Store
    fun abrirBateria(context: Context) =
        abrir(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pacote(context)))

    fun abrirInicioAutomaticoXiaomi(context: Context) = abrir(
        context,
        Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ),
    )

    fun abrirOutrasPermissoesXiaomi(context: Context) = abrir(
        context,
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", context.packageName),
    )

    fun abrirDetalhes(context: Context) =
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pacote(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    private fun pacote(context: Context): Uri = Uri.parse("package:${context.packageName}")

    /** Abre a tela pedida ou, se o sistema não tiver essa tela, os detalhes do app. */
    private fun abrir(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            abrirDetalhes(context)
        }
    }
}

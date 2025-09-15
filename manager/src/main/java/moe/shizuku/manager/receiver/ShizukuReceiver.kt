package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.adb.AdbWirelessHelper
import moe.shizuku.manager.model.ServiceStatus
import moe.shizuku.manager.shell.ShellBinderRequestHandler
import moe.shizuku.manager.starter.SelfStarterService

class ShizukuReceiver : BroadcastReceiver() {
    private val adbWirelessHelper = AdbWirelessHelper()

    override fun onReceive(context: Context, intent: Intent) {
        if ("rikka.shizuku.intent.action.REQUEST_BINDER" == intent.action) {
            ShellBinderRequestHandler.handleRequest(context, intent)
        }
        /* FIXME comment it for: Unable to start receiver moe.shizuku.manager.receiver.ShizukuReceiver: android.app.BackgroundServiceStartNotAllowedException: Not allowed to start service
        if (!ServiceStatus().isRunning) {
            val startOnBootWirelessIsEnabled = ShizukuSettings.getPreferences()
                .getBoolean(ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS, false)
            if (startOnBootWirelessIsEnabled) {
                val wirelessAdbStatus = adbWirelessHelper.validateThenEnableWirelessAdb(
                    context.contentResolver, context
                )
                if (wirelessAdbStatus) {
                    val intentService = Intent(context, SelfStarterService::class.java)
                    context.startService(intentService)
                }
            }
        }*/
    }
}

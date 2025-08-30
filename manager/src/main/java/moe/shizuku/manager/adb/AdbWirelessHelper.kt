package moe.shizuku.manager.adb

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moe.shizuku.manager.AppConstants
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.ADB_ROOT
import moe.shizuku.manager.ShizukuSettings.TCPIP_PORT
import moe.shizuku.manager.starter.Starter
import moe.shizuku.manager.starter.StarterActivity
import java.net.Socket

class AdbWirelessHelper {

    fun validateThenEnableWirelessAdb(
        contentResolver: ContentResolver,
        context: Context,
        wait: Boolean = false
    ): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (wait) {
            val timeoutMs = 15_000L
            val intervalMs = 500L
            var elapsed = 0L

            runBlocking {
                while (elapsed < timeoutMs) {
                    val networkCapabilities =
                        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
                    if (networkCapabilities != null && networkCapabilities.hasTransport(
                            NetworkCapabilities.TRANSPORT_WIFI
                        )
                    ) {
                        enableWirelessADB(contentResolver, context)
                        return@runBlocking
                    }
                    delay(intervalMs)
                    elapsed += intervalMs
                }
            }
        }

        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        if (networkCapabilities != null && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            enableWirelessADB(contentResolver, context)
            return true
        } else {
            Log.w(AppConstants.TAG, "Wireless ADB auto-start condition not met: Not on Wi-Fi.")
        }
        return false
    }

    private fun enableWirelessADB(contentResolver: ContentResolver, context: Context) {
        // Enable wireless ADB
        try {
            Settings.Global.putInt(contentResolver, "adb_wifi_enabled", 1)
            Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putLong(contentResolver, "adb_allowed_connection_time", 0L)

            Log.i(AppConstants.TAG, "Wireless Debugging enabled via secure setting.")
            Toast.makeText(context, "Wireless Debugging enabled", Toast.LENGTH_SHORT).show()
        } catch (se: SecurityException) {
            Log.e(AppConstants.TAG, "Permission denied trying to enable wireless debugging.", se)
            throw se
        } catch (e: Exception) {
            Log.e(AppConstants.TAG, "Error enabling wireless debugging.", e)
            throw e
        }
    }

    fun launchStarterActivity(context: Context, host: String, port: Int) {
        val intent = Intent(context, StarterActivity::class.java).apply {
            putExtra(StarterActivity.EXTRA_IS_ROOT, false)
            putExtra(StarterActivity.EXTRA_HOST, host)
            putExtra(StarterActivity.EXTRA_PORT, port)
        }
        context.startActivity(intent)
    }

    private fun executeAdbRootIfNeeded(
        host: String,
        port: Int,
        key: AdbKey,
        commandOutput: StringBuilder,
        onOutput: (String) -> Unit
    ): Boolean {
        if (!ShizukuSettings.getPreferences().getBoolean(ADB_ROOT, false)) {
            return false
        }

        AdbClient(host, port, key).use { client ->
            client.connect()

            var flag = false
            client.root {
                commandOutput.append(String(it).apply {
                    if (contains("adbd is already running as root") || contains("restarting adbd as root")) flag = true
                }).append("\n")
                onOutput(commandOutput.toString())
            }

            return flag
        }
    }


    private fun changeTcpipPortIfNeeded(
        host: String,
        port: Int,
        newPort: Int,
        key: AdbKey,
        commandOutput: StringBuilder,
        onOutput: (String) -> Unit
    ): Boolean {
        if (newPort < 1 || newPort > 65535) {
            Log.w(AppConstants.TAG, "Invalid TCP/IP port: $newPort")
            return false
        }

        AdbClient(host, port, key).use { client ->
            client.connect()

            var flag = false
            client.tcpip(newPort) {
                commandOutput.append(String(it).apply {
                    if (contains(Regex("restarting in TCP mode port: [0-9]*"))) flag = true
                }).append("\n")
                onOutput(commandOutput.toString())
            }

            return flag
        }
    }

    private fun waitForAdbPortAvailable(
        host: String,
        port: Int,
        timeoutMs: Long = 15000L
    ): Boolean {
        val intervalMs = 300L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            try {
                Socket(host, port).use {
                    return true
                }
            } catch (_: Exception) {
                Thread.sleep(intervalMs)
                elapsed += intervalMs
            }
        }
        return false
    }

    fun startShizukuViaAdb(
        host: String,
        port: Int,
        coroutineScope: CoroutineScope,
        onOutput: (String) -> Unit,
        onError: (Throwable) -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d(AppConstants.TAG, "Attempting to start Shizuku via ADB on $host:$port")

                val key = try {
                    AdbKey(
                        PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku"
                    )
                } catch (e: Throwable) {
                    Log.e(AppConstants.TAG, "ADB Key error", e)
                    onError(AdbKeyException(e))
                    return@launch
                }

                val commandOutput = StringBuilder()

                executeAdbRootIfNeeded(host, port, key, commandOutput, onOutput)
                var newPort: Int = -1
                ShizukuSettings.getPreferences().getString(TCPIP_PORT, "").let {
                    if (it.isNullOrEmpty())
                        true
                    else try {
                        newPort = it.toInt()
                        false
                    } catch (_: NumberFormatException) {
                        true
                    }
                }
                val finalPort =
                    if (changeTcpipPortIfNeeded(
                            host,
                            port,
                            newPort,
                            key,
                            commandOutput,
                            onOutput
                        )
                    ) {
                        if (!waitForAdbPortAvailable(host, newPort)) {
                            Log.w(
                                AppConstants.TAG,
                                "Timeout waiting for ADB to listen on new port $newPort"
                            )
                            onError(Exception("Timeout waiting for ADB to listen on new port $newPort"))
                            return@launch
                        }
                        newPort
                    } else port

                AdbClient(host, finalPort, key).use { client ->
                    try {
                        client.connect()
                        Log.i(
                            AppConstants.TAG,
                            "ADB connected to $host:$port. Executing starter command..."
                        )

                        client.shellCommand(Starter.internalCommand) { output ->
                            val outputString = String(output)
                            commandOutput.append(outputString)
                            onOutput(outputString)
                            Log.d(AppConstants.TAG, "Shizuku start output chunk: $outputString")
                        }

                        client.shellCommand(Starter.sdcardCommand) { output ->
                            val outputString = String(output)
                            commandOutput.append(outputString)
                            onOutput(outputString)
                            Log.d(AppConstants.TAG, "start.sh output chunk: $outputString")
                        }
                    } catch (e: Throwable) {
                        Log.e(AppConstants.TAG, "Error during ADB connection/command execution", e)
                        onError(e)
                        return@launch
                    }
                }

                Log.i(AppConstants.TAG, "Shizuku start via ADB completed successfully")
                onSuccess()
            } catch (e: Throwable) {
                Log.e(AppConstants.TAG, "Error in startShizukuViaAdb", e)
                onError(e)
            }
        }
    }
}

package moe.shizuku.manager.starter

import moe.shizuku.manager.application
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.system.ErrnoException
import android.system.Os
import moe.shizuku.manager.R
import moe.shizuku.manager.ktx.createDeviceProtectedStorageContextCompat
import moe.shizuku.manager.ktx.logd
import moe.shizuku.manager.ktx.loge
import java.io.*

object Starter {

    private val starterFile = File(application.applicationInfo.nativeLibraryDir, "libshizuku.so")

    val userCommand: String = starterFile.absolutePath

    val adbCommand = "adb shell $userCommand"

    val internalCommand = "$userCommand --apk=${application.applicationInfo.sourceDir}"

    /* FIXME Restore start.sh */
    private var commandInternal = arrayOfNulls<String>(2)

    val dataCommand get() = commandInternal[0]!!

    val sdcardCommand get() = commandInternal[1]!!

    fun writeSdcardFiles(context: Context) {
        /* FIXME Force writing start.sh everytime to ensure it's new code.
        if (commandInternal[1] != null) {
            logd("already written")
            return
        }
        */

        val um = context.getSystemService(UserManager::class.java)!!
        val unlocked = Build.VERSION.SDK_INT < 24 || um.isUserUnlocked
        if (!unlocked) {
            throw IllegalStateException("User is locked")
        }

        val filesDir = context.getExternalFilesDir(null) ?: throw IOException("getExternalFilesDir() returns null")
        val dir = filesDir.parentFile ?: throw IOException("$filesDir parentFile returns null")
        val sh = writeScript(context, File(dir, "start.sh"))
        commandInternal[1] = "sh $sh"
        logd(commandInternal[1]!!)
    }

    fun writeDataFiles(context: Context, permission: Boolean = false) {
        /* FIXME Force writing start.sh everytime to ensure it's new code.
        if (commandInternal[0] != null && !permission) {
            logd("already written")
            return
        }
        */

        val dir = context.createDeviceProtectedStorageContextCompat().filesDir?.parentFile ?: return

        if (permission) {
            try {
                Os.chmod(dir.absolutePath, 457 /* 0711 */)
            } catch (e: ErrnoException) {
                e.printStackTrace()
            }
        }

        try {
            val sh = writeScript(context, File(dir, "start.sh"))
            commandInternal[0] = "sh $sh --apk=${context.applicationInfo.sourceDir}"
            logd(commandInternal[0]!!)

            if (permission) {
                try {
                    Os.chmod(sh, 420 /* 0644 */)
                } catch (e: ErrnoException) {
                    e.printStackTrace()
                }
            }
        } catch (e: IOException) {
            loge("write files", e)
        }
    }

    private fun writeScript(context: Context, out: File): String {
        if (!out.exists()) {
            out.createNewFile()
        }
        val `is` = BufferedReader(InputStreamReader(context.resources.openRawResource(R.raw.start)))
        val os = PrintWriter(FileWriter(out))
        var line: String?
        while (`is`.readLine().also { line = it } != null) {
            os.println(line)
        }
        os.flush()
        os.close()
        return out.absolutePath
    }
}

package moe.shizuku.manager.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.BuildConfig
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ShizukuSettings.KEEP_START_ON_BOOT
import moe.shizuku.manager.ShizukuSettings.KEEP_START_ON_BOOT_WIRELESS
import moe.shizuku.manager.ShizukuSettings.TCPIP_PORT
import moe.shizuku.manager.app.ThemeHelper
import moe.shizuku.manager.app.ThemeHelper.KEY_BLACK_NIGHT_THEME
import moe.shizuku.manager.app.ThemeHelper.KEY_USE_SYSTEM_COLOR
import moe.shizuku.manager.ktx.TAG
import moe.shizuku.manager.ktx.isComponentEnabled
import moe.shizuku.manager.ktx.setComponentEnabled
import moe.shizuku.manager.ktx.toHtml
import moe.shizuku.manager.receiver.BootCompleteReceiver
import moe.shizuku.manager.utils.CustomTabsHelper
import moe.shizuku.manager.utils.ShizukuSystemApis
import rikka.core.util.ClipboardUtils
import rikka.core.util.ResourceUtils
import rikka.html.text.HtmlCompat
import rikka.material.app.LocaleDelegate
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku
import rikka.shizuku.manager.ShizukuLocales
import rikka.widget.borderview.BorderRecyclerView
import java.util.Locale
import moe.shizuku.manager.ShizukuSettings.LANGUAGE as KEY_LANGUAGE
import moe.shizuku.manager.ShizukuSettings.NIGHT_MODE as KEY_NIGHT_MODE

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var languagePreference: ListPreference
    private lateinit var nightModePreference: IntegerSimpleMenuPreference
    private lateinit var blackNightThemePreference: TwoStatePreference
    private lateinit var startOnBootPreference: TwoStatePreference
    private lateinit var startOnBootWirelessPreference: TwoStatePreference
    private lateinit var startupPreference: PreferenceCategory
    private lateinit var translationPreference: Preference
    private lateinit var translationContributorsPreference: Preference
    private lateinit var useSystemColorPreference: TwoStatePreference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = requireContext()

        preferenceManager.setStorageDeviceProtected()
        preferenceManager.sharedPreferencesName = ShizukuSettings.NAME
        preferenceManager.sharedPreferencesMode = Context.MODE_PRIVATE
        setPreferencesFromResource(R.xml.settings, null)

        languagePreference = findPreference(KEY_LANGUAGE)!!
        nightModePreference = findPreference(KEY_NIGHT_MODE)!!
        blackNightThemePreference = findPreference(KEY_BLACK_NIGHT_THEME)!!
        startOnBootPreference = findPreference(KEEP_START_ON_BOOT)!!
        startOnBootWirelessPreference = findPreference(KEEP_START_ON_BOOT_WIRELESS)!!
        startupPreference = findPreference("startup")!!
        translationPreference = findPreference("translation")!!
        translationContributorsPreference = findPreference("translation_contributors")!!
        useSystemColorPreference = findPreference(KEY_USE_SYSTEM_COLOR)!!

        val componentName =
            ComponentName(context.packageName, BootCompleteReceiver::class.java.name)
        // Initialize toggles based on saved preferences
        updatePreferenceStates(componentName)

        startOnBootPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue is Boolean) {
                    if (newValue) {
                        // These two options are mutually exclusive. So, disable the wireless option
                        startOnBootWirelessPreference.isChecked = false
                        savePreference(KEEP_START_ON_BOOT_WIRELESS, false)
                    }
                    toggleBootComponent(
                        componentName,
                        KEEP_START_ON_BOOT,
                        newValue || startOnBootWirelessPreference.isChecked
                    )
                } else false
            }

        startOnBootWirelessPreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val hasSecurePermission = ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_SECURE_SETTINGS
                ) == PackageManager.PERMISSION_GRANTED
                if (newValue is Boolean) {
                    if (newValue) {
                        // Check for permission
                        if (!hasSecurePermission) {

                            val dialog = MaterialAlertDialogBuilder(context)
                                .setMessage(
                                    getString(R.string.settings_grant_note).toHtml()
                                )

                            val click: DialogInterface.OnClickListener =
                                DialogInterface.OnClickListener { dialog, which ->
                                    val command =
                                        "adb shell pm grant " + BuildConfig.APPLICATION_ID + " android.permission.WRITE_SECURE_SETTINGS"
                                    MaterialAlertDialogBuilder(context)
                                        .setTitle(R.string.home_adb_button_view_command)
                                        .setMessage(
                                            HtmlCompat.fromHtml(
                                                context.getString(
                                                    R.string.home_adb_dialog_view_command_message,
                                                    command
                                                )
                                            )
                                        )
                                        .setPositiveButton(R.string.home_adb_dialog_view_command_copy_button) { _, _ ->
                                            if (ClipboardUtils.put(context, command)) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(
                                                        R.string.toast_copied_to_clipboard,
                                                        command
                                                    ),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .setNeutralButton(R.string.home_adb_dialog_view_command_button_send) { _, _ ->
                                            var intent = Intent(Intent.ACTION_SEND)
                                            intent.type = "text/plain"
                                            intent.putExtra(Intent.EXTRA_TEXT, command)
                                            intent = Intent.createChooser(
                                                intent,
                                                context.getString(R.string.home_adb_dialog_view_command_button_send)
                                            )
                                            context.startActivity(intent)
                                        }
                                        .show()
                                }
                            if (Shizuku.pingBinder()) {
                                dialog.setNeutralButton(R.string.cancel) { dialog, which -> }
                                    .setNegativeButton(R.string.manual, click)
                                    .setPositiveButton(R.string.auto) { dialog, which ->
                                        Log.i(
                                            ShizukuSettings.NAME,
                                            "Grant manager WRITE_SECURE_SETTINGS permission"
                                        )
                                        ShizukuSystemApis.grantRuntimePermission(
                                            BuildConfig.APPLICATION_ID,
                                            Manifest.permission.WRITE_SECURE_SETTINGS,
                                            0
                                        )
                                    }
                            } else {
                                dialog.setNegativeButton(R.string.cancel) { dialog, which -> }
                                    .setPositiveButton(R.string.manual, click)
                            }
                            dialog.show()
                            return@OnPreferenceChangeListener false
                        }

                        // Disable the root option because, mutual exclusivity
                        startOnBootPreference.isChecked = false
                        savePreference(KEEP_START_ON_BOOT, false)
                    }
                    toggleBootComponent(
                        componentName,
                        KEEP_START_ON_BOOT_WIRELESS,
                        newValue || startOnBootPreference.isChecked
                    )
                } else false
            }

        languagePreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                if (newValue is String) {
                    val locale: Locale = if ("SYSTEM" == newValue) {
                        LocaleDelegate.systemLocale
                    } else {
                        Locale.forLanguageTag(newValue)
                    }
                    LocaleDelegate.defaultLocale = locale
                    activity?.recreate()
                }
                true
            }

        setupLocalePreference()

        nightModePreference.value = ShizukuSettings.getNightMode()
        nightModePreference.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, value: Any? ->
                if (value is Int) {
                    if (ShizukuSettings.getNightMode() != value) {
                        AppCompatDelegate.setDefaultNightMode(value)
                        activity?.recreate()
                    }
                }
                true
            }
        if (ShizukuSettings.getNightMode() != AppCompatDelegate.MODE_NIGHT_NO) {
            blackNightThemePreference.isChecked = ThemeHelper.isBlackNightTheme(context)
            blackNightThemePreference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, _: Any? ->
                    if (ResourceUtils.isNightMode(context.resources.configuration)) {
                        activity?.recreate()
                    }
                    true
                }
        } else {
            blackNightThemePreference.isVisible = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            useSystemColorPreference.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, value: Any? ->
                    if (value is Boolean) {
                        if (ThemeHelper.isUsingSystemColor() != value) {
                            activity?.recreate()
                        }
                    }
                    true
                }
        } else {
            useSystemColorPreference.isVisible = false
        }

        translationPreference.summary = context.getString(
            R.string.settings_translation_summary, context.getString(R.string.app_name)
        )
        translationPreference.setOnPreferenceClickListener {
            CustomTabsHelper.launchUrlOrCopy(context, context.getString(R.string.translation_url))
            true
        }

        val contributors = context.getString(R.string.translation_contributors).toHtml().toString()
        if (contributors.isNotBlank()) {
            translationContributorsPreference.summary = contributors
        } else {
            translationContributorsPreference.isVisible = false
        }
        findPreference<EditTextPreference>(TCPIP_PORT)?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_NUMBER
        }
    }

    override fun onCreateRecyclerView(
        inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?
    ): RecyclerView {
        val recyclerView =
            super.onCreateRecyclerView(inflater, parent, savedInstanceState) as BorderRecyclerView
        recyclerView.fixEdgeEffect()
        recyclerView.addEdgeSpacing(bottom = 8f, unit = TypedValue.COMPLEX_UNIT_DIP)

        val lp = recyclerView.layoutParams
        if (lp is FrameLayout.LayoutParams) {
            lp.rightMargin =
                recyclerView.context.resources.getDimension(R.dimen.rd_activity_horizontal_margin)
                    .toInt()
            lp.leftMargin = lp.rightMargin
        }

        return recyclerView
    }

    private fun setupLocalePreference() {
        val localeTags = ShizukuLocales.LOCALES
        val displayLocaleTags = ShizukuLocales.DISPLAY_LOCALES

        languagePreference.entries = displayLocaleTags
        languagePreference.entryValues = localeTags

        val currentLocaleTag = languagePreference.value
        val currentLocaleIndex = localeTags.indexOf(currentLocaleTag)
        val currentLocale = ShizukuSettings.getLocale()
        val localizedLocales = mutableListOf<CharSequence>()

        for ((index, displayLocale) in displayLocaleTags.withIndex()) {
            if (index == 0) {
                localizedLocales.add(getString(R.string.follow_system))
                continue
            }

            val locale = Locale.forLanguageTag(displayLocale.toString())
            val localeName = if (!TextUtils.isEmpty(locale.script)) locale.getDisplayScript(locale)
            else locale.getDisplayName(locale)

            val localizedLocaleName =
                if (!TextUtils.isEmpty(locale.script)) locale.getDisplayScript(currentLocale)
                else locale.getDisplayName(currentLocale)

            localizedLocales.add(
                if (index != currentLocaleIndex) {
                    "$localeName<br><small>$localizedLocaleName<small>".toHtml()
                } else {
                    localizedLocaleName
                }
            )
        }

        languagePreference.entries = localizedLocales.toTypedArray()

        languagePreference.summary = when {
            TextUtils.isEmpty(currentLocaleTag) || "SYSTEM" == currentLocaleTag -> {
                getString(R.string.follow_system)
            }

            currentLocaleIndex != -1 -> {
                val localizedLocale = localizedLocales[currentLocaleIndex]
                val newLineIndex = localizedLocale.indexOf('\n')
                if (newLineIndex == -1) {
                    localizedLocale.toString()
                } else {
                    localizedLocale.subSequence(0, newLineIndex).toString()
                }
            }

            else -> {
                ""
            }
        }
    }

    private fun savePreference(key: String, value: Boolean) {
        ShizukuSettings.getPreferences().edit() { putBoolean(key, value) }
    }

    private fun updatePreferenceStates(componentName: ComponentName) {
        val isComponentEnabled = context?.packageManager?.isComponentEnabled(componentName) == true
        // Set startOnBootWirelessPreference as default
        val isWirelessBootEnabled =
            ShizukuSettings.getPreferences().getBoolean(KEEP_START_ON_BOOT_WIRELESS, true)
        val hasSecurePermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

        startOnBootPreference.isChecked = isComponentEnabled && !isWirelessBootEnabled
        startOnBootWirelessPreference.isChecked =
            isComponentEnabled && isWirelessBootEnabled && hasSecurePermission
    }

    private fun toggleBootComponent(
        componentName: ComponentName, key: String, enabled: Boolean
    ): Boolean {
        savePreference(key, enabled)

        try {
            context?.packageManager?.setComponentEnabled(componentName, enabled)

            val isEnabled = context?.packageManager?.isComponentEnabled(componentName) == enabled
            if (!isEnabled) {
                Log.e(TAG, "Failed to set component state: $componentName to $enabled")
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.wireless_boot_component_error), e)
            return false
        }

        return true
    }
}

package com.scrollstop.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/**
 * Robust detector for Input Method Editors (keyboards) and transient system UI overlays.
 *
 * Guarantees that keyboard appearances, soft input mode transitions, system overlays,
 * and dialogs do not prematurely terminate live limit monitoring for an underlying restricted app.
 *
 * Detection Layers:
 * 1. Fast O(1) matching for system UI overlays (android, com.android.systemui, *.systemui).
 * 2. Signature matching for dominant Android keyboards (Gboard, Samsung Honeyboard, SwiftKey, etc.).
 * 3. Generic naming heuristics (*.inputmethod.*, *.keyboard, *.latinime, *.ime).
 * 4. Dynamic query of [InputMethodManager.getEnabledInputMethodList] and [InputMethodManager.getInputMethodList].
 * 5. Dynamic query of [PackageManager.queryIntentServices] for android.view.InputMethod.
 * 6. Parsing of [Settings.Secure.ENABLED_INPUT_METHODS] and [Settings.Secure.DEFAULT_INPUT_METHOD].
 */
class ImePackageDetector(
    private val context: Context,
    private val timeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val cachedImePackages = mutableSetOf<String>()
    private var lastQueryTimeMs = 0L

    fun isImeOrTransient(packageName: String): Boolean {
        val pkg = packageName.trim().lowercase()
        if (pkg.isEmpty()) return false

        // 1. Transient system overlays and framework dialogs
        if (pkg == "android" || pkg == "com.android.systemui" || pkg.endsWith(".systemui")) {
            return true
        }

        // 2. Dominant Android keyboards
        if (pkg == "com.google.android.inputmethod.latin" ||
            pkg == "com.samsung.android.honeyboard" ||
            pkg == "com.touchtype.swiftkey" ||
            pkg == "com.microsoft.keyboard" ||
            pkg == "com.nuance.swype.dtc" ||
            pkg == "com.syntellia.fleksy.keyboard" ||
            pkg == "com.baidu.input" ||
            pkg == "com.sohu.inputmethod.sogou"
        ) {
            return true
        }

        // 3. Generic naming patterns for third-party or OEM keyboards
        if (pkg.contains("inputmethod") ||
            pkg.contains("latinime") ||
            pkg.contains("keyboard") ||
            pkg.endsWith(".ime") ||
            pkg.contains(".ime.")
        ) {
            return true
        }

        // 4-6. Query system services with 10-second refresh cache
        val now = timeProvider()
        if (cachedImePackages.isEmpty() || now - lastQueryTimeMs > 10_000L) {
            refreshImePackages()
            lastQueryTimeMs = now
        }

        return cachedImePackages.contains(pkg)
    }

    private fun refreshImePackages() {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.forEach {
                cachedImePackages.add(it.packageName.lowercase())
            }
            imm?.inputMethodList?.forEach {
                cachedImePackages.add(it.packageName.lowercase())
            }
        } catch (_: Throwable) {}

        try {
            val pm = context.packageManager
            val intent = Intent("android.view.InputMethod")
            val services = pm.queryIntentServices(intent, 0)
            services.forEach {
                it.serviceInfo?.packageName?.let { p ->
                    cachedImePackages.add(p.lowercase())
                }
            }
        } catch (_: Throwable) {}

        try {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_INPUT_METHODS
            )
            if (!enabled.isNullOrBlank()) {
                enabled.split(":").forEach { entry ->
                    val slashIdx = entry.indexOf('/')
                    if (slashIdx > 0) {
                        cachedImePackages.add(entry.substring(0, slashIdx).lowercase())
                    }
                }
            }

            val defaultIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            if (!defaultIme.isNullOrBlank()) {
                val slashIdx = defaultIme.indexOf('/')
                if (slashIdx > 0) {
                    cachedImePackages.add(defaultIme.substring(0, slashIdx).lowercase())
                }
            }
        } catch (_: Throwable) {}
    }
}

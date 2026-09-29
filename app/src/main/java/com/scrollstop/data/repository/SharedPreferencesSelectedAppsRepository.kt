package com.scrollstop.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SharedPreferences]-backed persistent implementation of [SelectedAppsRepository].
 *
 * Ensures user-selected application packages persist across Activity lifecycles, app restarts,
 * and independent background services like [AccessibilityService].
 */
class SharedPreferencesSelectedAppsRepository(
    context: Context,
    prefName: String = PREF_NAME
) : SelectedAppsRepository {

    companion object {
        const val PREF_NAME = "scrollstop_selected_apps_prefs"
        private const val KEY_SELECTED_PACKAGES = "selected_package_names"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    private val _selectedPackageNames = MutableStateFlow<Set<String>>(readFromPrefs())
    override val selectedPackageNames: StateFlow<Set<String>> = _selectedPackageNames.asStateFlow()

    private fun readFromPrefs(): Set<String> {
        val stringSet = prefs.getStringSet(KEY_SELECTED_PACKAGES, emptySet()) ?: emptySet()
        return stringSet.toSet()
    }

    private fun writeToPrefs(packages: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_SELECTED_PACKAGES, packages)
            .apply()
        _selectedPackageNames.value = packages
    }

    override fun getSelectedPackages(): Set<String> {
        return _selectedPackageNames.value
    }

    override fun setSelectedPackages(packages: Set<String>) {
        writeToPrefs(packages.toSet())
    }

    override fun toggleAppSelection(packageName: String) {
        val current = _selectedPackageNames.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        writeToPrefs(current)
    }

    override fun isSelected(packageName: String): Boolean {
        return _selectedPackageNames.value.contains(packageName)
    }
}

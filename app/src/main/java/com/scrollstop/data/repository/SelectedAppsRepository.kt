package com.scrollstop.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository interface for managing user-selected applications in memory.
 */
interface SelectedAppsRepository {
    val selectedPackageNames: StateFlow<Set<String>>
    fun getSelectedPackages(): Set<String>
    fun setSelectedPackages(packages: Set<String>)
    fun toggleAppSelection(packageName: String)
    fun isSelected(packageName: String): Boolean
}

/**
 * In-memory implementation of [SelectedAppsRepository].
 */
class InMemorySelectedAppsRepository : SelectedAppsRepository {

    private val _selectedPackageNames = MutableStateFlow<Set<String>>(emptySet())
    override val selectedPackageNames: StateFlow<Set<String>> = _selectedPackageNames.asStateFlow()

    override fun getSelectedPackages(): Set<String> {
        return _selectedPackageNames.value
    }

    override fun setSelectedPackages(packages: Set<String>) {
        _selectedPackageNames.value = packages.toSet()
    }

    override fun toggleAppSelection(packageName: String) {
        val current = _selectedPackageNames.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _selectedPackageNames.value = current
    }

    override fun isSelected(packageName: String): Boolean {
        return _selectedPackageNames.value.contains(packageName)
    }
}

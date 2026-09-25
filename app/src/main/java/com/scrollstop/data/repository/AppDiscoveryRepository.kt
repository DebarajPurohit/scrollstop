package com.scrollstop.data.repository

import com.scrollstop.domain.model.DiscoveredApp

/**
 * Interface for discovering launchable applications on the user's device.
 */
interface AppDiscoveryRepository {
    /**
     * Queries and returns the list of launchable applications discovered on the device,
     * deduplicated, sorted alphabetically, and excluding Stop Doom Scroll.
     */
    suspend fun getDiscoveredApps(): Result<List<DiscoveredApp>>
}

package com.scrollstop.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.scrollstop.domain.model.DiscoveredApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of [AppDiscoveryRepository] using Android [PackageManager].
 * Discovers user-launchable activities matching ACTION_MAIN + CATEGORY_LAUNCHER.
 *
 * @param context Android application or activity context.
 * @param ownPackageName Package name of Stop Doom Scroll to exclude (defaults to context.packageName).
 */
class PackageManagerAppDiscoveryRepository(
    private val context: Context,
    private val ownPackageName: String = context.packageName
) : AppDiscoveryRepository {

    override suspend fun getDiscoveredApps(): Result<List<DiscoveredApp>> = withContext(Dispatchers.IO) {
        runCatching {
            val packageManager = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos = packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_DEFAULT_ONLY.takeIf { it != 0 } ?: 0
            )

            processResolveInfos(packageManager, resolveInfos, ownPackageName)
        }
    }

    companion object {
        /**
         * Core processing logic separated for testability.
         * Deduplicates packages, excludes self package, loads labels/icons safely, and sorts deterministically.
         */
        fun processResolveInfos(
            packageManager: PackageManager,
            resolveInfos: List<ResolveInfo>?,
            ownPackageName: String
        ): List<DiscoveredApp> {
            if (resolveInfos.isNullOrEmpty()) return emptyList()

            // Group resolveInfos by packageName to eliminate duplicate launcher activities
            val groupedByPackage = resolveInfos
                .filter { it.activityInfo?.packageName != null }
                .groupBy { it.activityInfo.packageName }

            val discoveredApps = mutableListOf<DiscoveredApp>()

            for ((packageName, infos) in groupedByPackage) {
                // Exclude Stop Doom Scroll itself
                if (packageName == ownPackageName) continue

                val mainInfo = infos.firstOrNull() ?: continue
                
                // Safely load label with fallbacks
                val label = try {
                    val rawLabel = mainInfo.loadLabel(packageManager).toString().trim()
                    if (rawLabel.isNotEmpty()) rawLabel else packageName
                } catch (e: Exception) {
                    packageName
                }

                // Safely load icon
                val icon = try {
                    mainInfo.loadIcon(packageManager)
                } catch (e: Exception) {
                    null
                }

                discoveredApps.add(
                    DiscoveredApp(
                        packageName = packageName,
                        appLabel = label,
                        icon = icon,
                        isSelected = false
                    )
                )
            }

            // Deterministic sorting: Primary by lowercase label, secondary by package name
            return discoveredApps.sortedWith(
                compareBy(
                    { it.appLabel.lowercase() },
                    { it.packageName }
                )
            )
        }
    }
}

package com.scrollstop.data.repository

import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PackageManagerAppDiscoveryRepositoryTest {

    private val ownPackageName = "com.scrollstop"
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val packageManager: PackageManager = context.packageManager

    @Test
    fun processResolveInfos_mapsDiscoveredAppInfoCorrectly() {
        val resolveInfo = createResolveInfo("com.example.social", "Social App")

        val result = PackageManagerAppDiscoveryRepository.processResolveInfos(
            packageManager = packageManager,
            resolveInfos = listOf(resolveInfo),
            ownPackageName = ownPackageName
        )

        assertEquals(1, result.size)
        val app = result.first()
        assertEquals("com.example.social", app.packageName)
        assertEquals("Social App", app.appLabel)
        assertFalse(app.isSelected)
    }

    @Test
    fun processResolveInfos_excludesSelfPackageName() {
        val selfInfo = createResolveInfo("com.scrollstop", "Stop Doom Scroll")
        val otherInfo = createResolveInfo("com.example.video", "Video App")

        val result = PackageManagerAppDiscoveryRepository.processResolveInfos(
            packageManager = packageManager,
            resolveInfos = listOf(selfInfo, otherInfo),
            ownPackageName = ownPackageName
        )

        assertEquals(1, result.size)
        assertEquals("com.example.video", result.first().packageName)
    }

    @Test
    fun processResolveInfos_handlesDuplicatePackagesSingleEntry() {
        val activity1 = createResolveInfo("com.example.multi", "Multi Activity 1")
        val activity2 = createResolveInfo("com.example.multi", "Multi Activity 2")

        val result = PackageManagerAppDiscoveryRepository.processResolveInfos(
            packageManager = packageManager,
            resolveInfos = listOf(activity1, activity2),
            ownPackageName = ownPackageName
        )

        assertEquals(1, result.size)
        assertEquals("com.example.multi", result.first().packageName)
    }

    @Test
    fun processResolveInfos_sortsAppsAlphabeticallyCaseInsensitive() {
        val appZ = createResolveInfo("com.z.app", "Zeta App")
        val appA = createResolveInfo("com.a.app", "alpha app")
        val appB = createResolveInfo("com.b.app", "Beta App")

        val result = PackageManagerAppDiscoveryRepository.processResolveInfos(
            packageManager = packageManager,
            resolveInfos = listOf(appZ, appA, appB),
            ownPackageName = ownPackageName
        )

        assertEquals(3, result.size)
        assertEquals("alpha app", result[0].appLabel)
        assertEquals("Beta App", result[1].appLabel)
        assertEquals("Zeta App", result[2].appLabel)
    }

    @Test
    fun processResolveInfos_handlesEmptyOrNullResolveInfosSafely() {
        val resultNull = PackageManagerAppDiscoveryRepository.processResolveInfos(
            packageManager = packageManager,
            resolveInfos = null,
            ownPackageName = ownPackageName
        )
        assertTrue(resultNull.isEmpty())

        val resultEmpty = PackageManagerAppDiscoveryRepository.processResolveInfos(
            packageManager = packageManager,
            resolveInfos = emptyList(),
            ownPackageName = ownPackageName
        )
        assertTrue(resultEmpty.isEmpty())
    }

    private fun createResolveInfo(packageName: String, label: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                this.name = "$packageName.MainActivity"
                this.applicationInfo = android.content.pm.ApplicationInfo().apply {
                    this.packageName = packageName
                }
            }
            nonLocalizedLabel = label
        }
    }
}

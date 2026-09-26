package com.scrollstop.data.repository

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.scrollstop.data.time.TimeProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidUsageStatsRepositoryTest {

    private lateinit var context: Context
    private lateinit var appOpsManager: AppOpsManager
    private lateinit var fakeTimeProvider: FakeTimeProvider
    private lateinit var repository: AndroidUsageStatsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        fakeTimeProvider = FakeTimeProvider(
            currentTime = 1600000000000L,
            startOfDay = 1600000000000L - (10 * 3600 * 1000L)
        )
        repository = AndroidUsageStatsRepository(context, fakeTimeProvider)
    }

    @Test
    fun `hasUsagePermission returns false when app ops permission mode is ignored`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )

        val permissionGranted = repository.hasUsagePermission()
        assertFalse(permissionGranted)
    }

    @Test
    fun `hasUsagePermission returns true when app ops permission mode is allowed`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ALLOWED
        )

        val permissionGranted = repository.hasUsagePermission()
        assertTrue(permissionGranted)
    }

    @Test
    fun `getTodayUsageForPackages returns failure when usage permission not granted`() {
        Shadows.shadowOf(appOpsManager).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_IGNORED
        )

        val result = repository.getTodayUsageForPackages(setOf("com.instagram.android"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    private class FakeTimeProvider(
        var currentTime: Long,
        var startOfDay: Long
    ) : TimeProvider {
        override fun currentTimeMillis(): Long = currentTime
        override fun getStartOfDayMillis(): Long = startOfDay
    }
}

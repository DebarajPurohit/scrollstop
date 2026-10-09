package com.scrollstop.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImePackageDetectorTest {

    private lateinit var context: Context
    private lateinit var detector: ImePackageDetector

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        detector = ImePackageDetector(context)
    }

    @Test
    fun `detects system framework and system ui packages as transient`() {
        assertTrue(detector.isImeOrTransient("android"))
        assertTrue(detector.isImeOrTransient("com.android.systemui"))
        assertTrue(detector.isImeOrTransient("com.google.android.systemui"))
        assertTrue(detector.isImeOrTransient("com.samsung.android.systemui"))
    }

    @Test
    fun `detects dominant OEM and third-party keyboards as transient`() {
        assertTrue(detector.isImeOrTransient("com.google.android.inputmethod.latin")) // Gboard
        assertTrue(detector.isImeOrTransient("com.samsung.android.honeyboard")) // Samsung Keyboard
        assertTrue(detector.isImeOrTransient("com.touchtype.swiftkey")) // Microsoft SwiftKey
        assertTrue(detector.isImeOrTransient("com.microsoft.keyboard"))
        assertTrue(detector.isImeOrTransient("com.nuance.swype.dtc"))
        assertTrue(detector.isImeOrTransient("com.syntellia.fleksy.keyboard"))
    }

    @Test
    fun `detects generic keyboard package name patterns as transient`() {
        assertTrue(detector.isImeOrTransient("com.example.custom.inputmethod.service"))
        assertTrue(detector.isImeOrTransient("com.oem.latinime"))
        assertTrue(detector.isImeOrTransient("com.fancy.mykeyboard"))
        assertTrue(detector.isImeOrTransient("org.pocketwork.ime"))
    }

    @Test
    fun `does not treat regular applications or launchers as transient`() {
        assertFalse(detector.isImeOrTransient("in.amazon.mShop.android.shopping"))
        assertFalse(detector.isImeOrTransient("com.google.android.youtube"))
        assertFalse(detector.isImeOrTransient("com.android.settings"))
        assertFalse(detector.isImeOrTransient("com.google.android.apps.nexuslauncher"))
        assertFalse(detector.isImeOrTransient("com.sec.android.app.launcher"))
        assertFalse(detector.isImeOrTransient("com.android.chrome"))
        assertFalse(detector.isImeOrTransient(""))
        assertFalse(detector.isImeOrTransient("   "))
    }
}

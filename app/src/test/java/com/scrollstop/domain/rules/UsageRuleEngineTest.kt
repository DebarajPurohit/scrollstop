package com.scrollstop.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UsageRuleEngineTest {

    private lateinit var engine: UsageRuleEngine
    private val limit2MinMs = 2 * 60 * 1000L // 120,000 ms

    @Before
    fun setUp() {
        engine = UsageRuleEngine()
    }

    @Test
    fun `test 1 - usage 0 ms, limit 2 min`() {
        val result = engine.evaluate("com.google.android.youtube", 0L, limit2MinMs)

        assertEquals("com.google.android.youtube", result.packageName)
        assertEquals(0L, result.usedDurationMs)
        assertEquals(limit2MinMs, result.limitDurationMs)
        assertEquals(limit2MinMs, result.remainingDurationMs)
        assertEquals(LimitState.NOT_STARTED, result.limitState)
    }

    @Test
    fun `test 2 - usage 30 sec, limit 2 min`() {
        val usage30SecMs = 30 * 1000L
        val result = engine.evaluate("com.google.android.youtube", usage30SecMs, limit2MinMs)

        assertEquals(usage30SecMs, result.usedDurationMs)
        assertEquals(limit2MinMs, result.limitDurationMs)
        assertEquals(90 * 1000L, result.remainingDurationMs)
        assertEquals(LimitState.WITHIN_LIMIT, result.limitState)
    }

    @Test
    fun `test 3 - usage exactly 2 min`() {
        val result = engine.evaluate("com.google.android.youtube", limit2MinMs, limit2MinMs)

        assertEquals(limit2MinMs, result.usedDurationMs)
        assertEquals(limit2MinMs, result.limitDurationMs)
        assertEquals(0L, result.remainingDurationMs)
        assertEquals(LimitState.LIMIT_REACHED, result.limitState)
    }

    @Test
    fun `test 4 - usage greater than 2 min`() {
        val usageAboveMs = 2 * 60 * 1000L + 5000L // 2 min 5 sec
        val result = engine.evaluate("com.google.android.youtube", usageAboveMs, limit2MinMs)

        assertEquals(usageAboveMs, result.usedDurationMs)
        assertEquals(limit2MinMs, result.limitDurationMs)
        assertEquals(0L, result.remainingDurationMs)
        assertEquals(LimitState.LIMIT_REACHED, result.limitState)
    }

    @Test
    fun `test 5 - usage 1 ms below limit`() {
        val usage1MsBelowMs = limit2MinMs - 1L // 119,999 ms
        val result = engine.evaluate("com.google.android.youtube", usage1MsBelowMs, limit2MinMs)

        assertEquals(usage1MsBelowMs, result.usedDurationMs)
        assertEquals(limit2MinMs, result.limitDurationMs)
        assertEquals(1L, result.remainingDurationMs)
        assertEquals(LimitState.WITHIN_LIMIT, result.limitState)
    }

    @Test
    fun `test 6 - usage 1 ms above limit`() {
        val usage1MsAboveMs = limit2MinMs + 1L // 120,001 ms
        val result = engine.evaluate("com.google.android.youtube", usage1MsAboveMs, limit2MinMs)

        assertEquals(usage1MsAboveMs, result.usedDurationMs)
        assertEquals(limit2MinMs, result.limitDurationMs)
        assertEquals(0L, result.remainingDurationMs)
        assertEquals(LimitState.LIMIT_REACHED, result.limitState)
    }

    @Test
    fun `test 7 - remaining time never negative`() {
        // Test extreme usage way above limit
        val extremeUsageMs = 10 * 60 * 60 * 1000L // 10 hours
        val result = engine.evaluate("com.google.android.youtube", extremeUsageMs, limit2MinMs)

        assertTrue("Remaining duration must be >= 0", result.remainingDurationMs >= 0L)
        assertEquals(0L, result.remainingDurationMs)
        assertEquals(LimitState.LIMIT_REACHED, result.limitState)

        // Test negative usage duration input handling
        val negativeUsageResult = engine.evaluate("com.google.android.youtube", -5000L, limit2MinMs)
        assertTrue("Remaining duration must be >= 0", negativeUsageResult.remainingDurationMs >= 0L)
        assertEquals(limit2MinMs, negativeUsageResult.remainingDurationMs)
        assertEquals(0L, negativeUsageResult.usedDurationMs)
    }

    @Test
    fun `test 11 - repeated evaluation produces the same result for the same inputs`() {
        val usedMs = 45 * 1000L
        val limitMs = 120 * 1000L

        val result1 = engine.evaluate("com.instagram.android", usedMs, limitMs)
        val result2 = engine.evaluate("com.instagram.android", usedMs, limitMs)
        val result3 = engine.evaluate("com.instagram.android", usedMs, limitMs)

        assertEquals(result1, result2)
        assertEquals(result2, result3)
    }

    @Test
    fun `test 12 - no Android or UI dependency in the core rules engine`() {
        val engineClass = UsageRuleEngine::class.java
        val resultClass = LimitEvaluationResult::class.java
        val stateClass = LimitState::class.java

        val classesToCheck = listOf(engineClass, resultClass, stateClass)

        for (clazz in classesToCheck) {
            val packageName = clazz.packageName
            assertTrue("Class must be in com.scrollstop.domain.rules", packageName.startsWith("com.scrollstop.domain.rules"))

            // Verify fields do not reference android framework
            for (field in clazz.declaredFields) {
                val fieldTypeName = field.type.name
                assertFalse("Field type ${field.name} ($fieldTypeName) cannot depend on android.*", fieldTypeName.startsWith("android."))
                assertFalse("Field type ${field.name} ($fieldTypeName) cannot depend on androidx.*", fieldTypeName.startsWith("androidx."))
            }

            // Verify method return types do not reference android framework
            for (method in clazz.declaredMethods) {
                val returnTypeName = method.returnType.name
                assertFalse("Method ${method.name} return type ($returnTypeName) cannot depend on android.*", returnTypeName.startsWith("android."))
                assertFalse("Method ${method.name} return type ($returnTypeName) cannot depend on androidx.*", returnTypeName.startsWith("androidx."))
            }
        }
    }
}

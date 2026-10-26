package com.scrollstop.ui.screens.limits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.scrollstop.domain.util.UsageFormatter
import com.scrollstop.ui.components.AppIconThumbnail
import com.scrollstop.ui.theme.extendedColors

/**
 * Standard preset durations available in production:
 * 15 min, 30 min, 45 min, 1 hour, 1.5 hours, 2 hours.
 */
data class LimitPreset(
    val durationMs: Long,
    val label: String
)

val StandardLimitPresets = listOf(
    LimitPreset(15 * 60 * 1000L, "15 min"),
    LimitPreset(30 * 60 * 1000L, "30 min"),
    LimitPreset(45 * 60 * 1000L, "45 min"),
    LimitPreset(60 * 60 * 1000L, "1 hour"),
    LimitPreset(90 * 60 * 1000L, "1.5 hours"),
    LimitPreset(120 * 60 * 1000L, "2 hours")
)

val DebugLimitPreset = LimitPreset(2 * 60 * 1000L, "2 min (Test)")

/**
 * Limit configuration modal dialog for editing daily allowances of monitored applications.
 *
 * Enforces:
 * - Immediate tightening vs staged relaxation under Commitment Lock
 * - Standard production presets (15m, 30m, 45m, 1h, 1.5h, 2h)
 * - Optional debug preset (2m) when [showDebugPresets] is enabled
 * - Custom duration picker (hours & minutes) with validation
 * - Clear indication of selected limit and staged change cancellation
 */
@Composable
fun LimitConfigurationModal(
    appItem: LimitsAppItem,
    isCommitmentLocked: Boolean = false,
    showDebugPresets: Boolean = com.scrollstop.BuildConfig.DEBUG,
    isLoading: Boolean = false,
    saveErrorMessage: String? = null,
    onDismiss: () -> Unit,
    onSaveLimit: (packageName: String, limitMs: Long) -> Unit,
    onCancelStagedLimitIncrease: ((packageName: String) -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = {
            if (!isLoading) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .clip(RoundedCornerShape(24.dp)),
        confirmButton = {},
        dismissButton = {},
        text = {
            LimitConfigurationContent(
                appItem = appItem,
                isCommitmentLocked = isCommitmentLocked,
                showDebugPresets = showDebugPresets,
                isLoading = isLoading,
                saveErrorMessage = saveErrorMessage,
                onDismiss = onDismiss,
                onSaveLimit = onSaveLimit,
                onCancelStagedLimitIncrease = onCancelStagedLimitIncrease
            )
        }
    )
}

/**
 * Core content of the limit configuration editor, decoupled from the dialog window
 * for direct composition and UI testing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LimitConfigurationContent(
    appItem: LimitsAppItem,
    isCommitmentLocked: Boolean = false,
    showDebugPresets: Boolean = com.scrollstop.BuildConfig.DEBUG,
    isLoading: Boolean = false,
    saveErrorMessage: String? = null,
    onDismiss: () -> Unit,
    onSaveLimit: (packageName: String, limitMs: Long) -> Unit,
    onCancelStagedLimitIncrease: ((packageName: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val presets = remember(showDebugPresets) {
        if (showDebugPresets) {
            listOf(DebugLimitPreset) + StandardLimitPresets
        } else {
            StandardLimitPresets
        }
    }

    // Determine initial selection: match preset if equal, otherwise custom
    val initialPresetMatch = remember(appItem.limitDurationMs, presets) {
        presets.firstOrNull { it.durationMs == appItem.limitDurationMs }
    }

    var selectedLimitMs by remember(appItem.limitDurationMs) {
        mutableLongStateOf(
            if (appItem.limitDurationMs > 0L) appItem.limitDurationMs else 30 * 60 * 1000L
        )
    }

    var isCustomMode by remember(appItem.limitDurationMs, initialPresetMatch) {
        mutableStateOf(initialPresetMatch == null && appItem.limitDurationMs > 0L)
    }

    // Custom duration hour / minute inputs
    var customHours by remember(appItem.limitDurationMs) {
        val totalMinutes = appItem.limitDurationMs / (60 * 1000L)
        mutableStateOf((totalMinutes / 60).toString())
    }

    var customMinutes by remember(appItem.limitDurationMs) {
        val totalMinutes = appItem.limitDurationMs / (60 * 1000L)
        mutableStateOf((totalMinutes % 60).toString())
    }

    var validationError by remember { mutableStateOf<String?>(null) }

    fun updateCustomLimit(hStr: String, mStr: String) {
        val h = hStr.toLongOrNull() ?: 0L
        val m = mStr.toLongOrNull() ?: 0L

        val totalMs = (h * 60 + m) * 60 * 1000L
        when {
            h < 0L || m < 0L -> {
                validationError = "Duration values cannot be negative."
            }
            totalMs < 60 * 1000L -> {
                validationError = "Daily limit must be at least 1 minute."
            }
            totalMs > 24 * 60 * 60 * 1000L -> {
                validationError = "Daily limit cannot exceed 24 hours."
            }
            m >= 60L -> {
                validationError = "Minutes must be between 0 and 59."
            }
            else -> {
                validationError = null
                selectedLimitMs = totalMs
            }
        }
    }

    fun sanitizeNumberInput(newVal: String): String {
        val digitsOnly = newVal.filter { it.isDigit() }
        return when {
            digitsOnly.isEmpty() -> ""
            digitsOnly.length > 2 -> digitsOnly.takeLast(2)
            digitsOnly.length == 2 && digitsOnly.startsWith("0") -> digitsOnly.substring(1)
            else -> digitsOnly
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .semantics { contentDescription = "Limit Configuration Modal for ${appItem.appLabel}" },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header: App Icon, Label, and Package
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconThumbnail(
                drawable = appItem.icon,
                appName = appItem.appLabel,
                packageName = appItem.packageName,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appItem.appLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = appItem.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Current Limit Indicator
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Daily Limit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = appItem.formattedLimit,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Staged Limit Increase Banner & Cancellation Option
        if (appItem.pendingLimitIncreaseMs != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.extendedColors.warningContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pending Limit Increase",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.extendedColors.onWarningContainer
                    )
                    Text(
                        text = "A new limit of ${appItem.formattedPendingLimit ?: UsageFormatter.formatDuration(appItem.pendingLimitIncreaseMs)} is scheduled to take effect at next midnight.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.onWarningContainer
                    )
                    if (onCancelStagedLimitIncrease != null) {
                        OutlinedButton(
                            onClick = { onCancelStagedLimitIncrease(appItem.packageName) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .semantics { contentDescription = "Cancel Staged Limit Increase" },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.extendedColors.onWarningContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.extendedColors.onWarningContainer)
                        ) {
                            Text(
                                text = "Cancel Scheduled Change",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 2. Preset Selection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Preset Limits",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = !isCustomMode && selectedLimitMs == preset.durationMs
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            isCustomMode = false
                            selectedLimitMs = preset.durationMs
                            val totalMinutes = preset.durationMs / (60 * 1000L)
                            customHours = (totalMinutes / 60).toString()
                            customMinutes = (totalMinutes % 60).toString()
                            validationError = null
                        },
                        label = {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .semantics { contentDescription = "Preset ${preset.label}" }
                    )
                }

                // Custom Duration Toggle Pill
                FilterChip(
                    selected = isCustomMode,
                    onClick = {
                        isCustomMode = true
                        if (customHours == "0" && customMinutes == "0" && selectedLimitMs >= 60 * 1000L) {
                            val totalMinutes = selectedLimitMs / (60 * 1000L)
                            customHours = (totalMinutes / 60).toString()
                            customMinutes = (totalMinutes % 60).toString()
                        }
                        updateCustomLimit(customHours, customMinutes)
                    },
                    label = {
                        Text(
                            text = "Custom...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isCustomMode) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .semantics { contentDescription = "Custom duration option" }
                )
            }
        }

        // 3. Custom Duration Picker (visible when custom mode is active)
        if (isCustomMode) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Custom duration inputs" }
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Specify Custom Duration",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hours Input
                        OutlinedTextField(
                            value = customHours,
                            onValueChange = { newVal ->
                                val cleaned = sanitizeNumberInput(newVal)
                                customHours = cleaned
                                updateCustomLimit(cleaned, customMinutes)
                            },
                            label = { Text("Hours") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Custom duration hours input" }
                        )

                        // Minutes Input
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { newVal ->
                                val cleaned = sanitizeNumberInput(newVal)
                                customMinutes = cleaned
                                updateCustomLimit(customHours, cleaned)
                            },
                            label = { Text("Minutes") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Custom duration minutes input" }
                        )
                    }

                    // Stepper row for quick minute tweaks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45).forEach { minStep ->
                            OutlinedButton(
                                onClick = {
                                    customMinutes = minStep.toString()
                                    updateCustomLimit(customHours, minStep.toString())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 36.dp)
                            ) {
                                Text(text = "$minStep m", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Validation Error Display
        if (validationError != null) {
            Text(
                text = validationError ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Validation error: $validationError" }
            )
        }

        // Save Error Display
        if (!saveErrorMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = saveErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // 4. New Limit Indication & Commitment Lock Notice
        val effectiveLimitMs = if (isCustomMode) {
            val h = customHours.toLongOrNull() ?: 0L
            val m = customMinutes.toLongOrNull() ?: 0L
            (h * 60 + m) * 60 * 1000L
        } else {
            selectedLimitMs
        }

        val isTightening = effectiveLimitMs < appItem.limitDurationMs
        val isRelaxing = effectiveLimitMs > appItem.limitDurationMs
        val isUnchanged = effectiveLimitMs == appItem.limitDurationMs

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isCommitmentLocked && isRelaxing -> MaterialTheme.extendedColors.warningContainer
                    isCommitmentLocked && isTightening -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Selected new limit preview" }
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected New Limit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isCommitmentLocked && isRelaxing -> MaterialTheme.extendedColors.onWarningContainer
                            isCommitmentLocked && isTightening -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = UsageFormatter.formatDuration(effectiveLimitMs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics {
                            contentDescription = "Selected new limit value: ${UsageFormatter.formatDuration(effectiveLimitMs)}"
                        },
                        color = when {
                            isCommitmentLocked && isRelaxing -> MaterialTheme.extendedColors.onWarningContainer
                            isCommitmentLocked && isTightening -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Plain-language Commitment Lock Rule Explanation
                val explanationText = when {
                    isUnchanged -> "Unchanged from current daily limit."
                    isCommitmentLocked && isTightening ->
                        "Decreasing your limit strengthens your protection and takes effect immediately."
                    isCommitmentLocked && isRelaxing ->
                        "Commitment Lock is active. Increasing your daily limit will take effect at next midnight (12:00 AM) to protect your commitment."
                    else ->
                        "New limit will take effect immediately upon saving."
                }

                Text(
                    text = explanationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isCommitmentLocked && isRelaxing -> MaterialTheme.extendedColors.onWarningContainer
                        isCommitmentLocked && isTightening -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 5. Action Buttons (Cancel / Save)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoading,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Cancel without saving" }
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val saveButtonDescription = if (isLoading) "Saving daily limit" else "Save limit"

            Button(
                onClick = {
                    if (validationError == null && effectiveLimitMs >= 60 * 1000L) {
                        onSaveLimit(appItem.packageName, effectiveLimitMs)
                    }
                },
                enabled = !isLoading && validationError == null && effectiveLimitMs >= 60 * 1000L,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = saveButtonDescription },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Save limit",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

package com.scrollstop.ui.screens.limits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scrollstop.ui.components.AppIconThumbnail
import com.scrollstop.ui.components.BadgeSeverity
import com.scrollstop.ui.components.ProtectionStatusBadge
import com.scrollstop.ui.components.ScrollStopTopBar
import com.scrollstop.ui.components.UsageProgressBar
import com.scrollstop.ui.theme.extendedColors

/**
 * Stateful entry point for the Unified Limits & Apps Screen.
 */
@Composable
fun LimitsScreen(
    viewModel: LimitsViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onRequestUsagePermission: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    showDebugPresets: Boolean = com.scrollstop.BuildConfig.DEBUG,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LimitsScreen(
        uiState = uiState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onFilterSelect = viewModel::onFilterSelect,
        onToggleAppSelection = viewModel::toggleAppSelection,
        onSetDailyLimit = { pkg, limitMs -> viewModel.setDailyLimit(pkg, limitMs) },
        onCancelStagedLimitIncrease = { pkg -> viewModel.cancelStagedLimitIncrease(pkg) },
        onCancelStagedRemoval = { pkg -> viewModel.cancelStagedRemoval(pkg) },
        onClearNotice = viewModel::clearNotice,
        onRefresh = viewModel::refresh,
        onNavigateBack = onNavigateBack,
        onRequestUsagePermission = onRequestUsagePermission,
        bottomBar = bottomBar,
        showDebugPresets = showDebugPresets,
        modifier = modifier
    )
}

/**
 * Stateless presentation composable for the Unified Limits & Apps Screen.
 */
@Composable
fun LimitsScreen(
    uiState: LimitsUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (LimitsFilter) -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onSetDailyLimit: (packageName: String, limitMs: Long) -> Unit,
    onCancelStagedLimitIncrease: (packageName: String) -> Unit,
    onCancelStagedRemoval: (packageName: String) -> Unit,
    onClearNotice: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onRequestUsagePermission: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    showDebugPresets: Boolean = com.scrollstop.BuildConfig.DEBUG,
    modifier: Modifier = Modifier
) {
    var editingAppItem by remember { mutableStateOf<LimitsAppItem?>(null) }

    Scaffold(
        topBar = {
            ScrollStopTopBar(
                title = "Limits & Apps",
                subtitle = "Control distracting apps with daily usage allowances",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.semantics { contentDescription = "Refresh apps and usage" }
                    ) {
                        Text(
                            text = "↻",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is LimitsUiState.Loading -> {
                    LoadingState()
                }

                is LimitsUiState.PermissionRequired -> {
                    PermissionRequiredState(onRequestUsagePermission = onRequestUsagePermission)
                }

                is LimitsUiState.Error -> {
                    ErrorState(
                        errorMessage = uiState.errorMessage,
                        onRetry = onRefresh
                    )
                }

                is LimitsUiState.Empty -> {
                    EmptyCatalogState(onRefresh = onRefresh)
                }

                is LimitsUiState.Success -> {
                    SuccessContent(
                        state = uiState,
                        onSearchQueryChange = onSearchQueryChange,
                        onFilterSelect = onFilterSelect,
                        onToggleAppSelection = onToggleAppSelection,
                        onOpenLimitEditor = { appItem -> editingAppItem = appItem },
                        onCancelStagedRemoval = onCancelStagedRemoval,
                        onClearNotice = onClearNotice
                    )
                }
            }

            // Edit Limit Modal
            editingAppItem?.let { app ->
                val isLocked = if (uiState is LimitsUiState.Success) uiState.isCommitmentLocked else false

                LimitConfigurationModal(
                    appItem = app,
                    isCommitmentLocked = isLocked,
                    showDebugPresets = showDebugPresets,
                    onDismiss = { editingAppItem = null },
                    onSaveLimit = { pkg, limitMs ->
                        onSetDailyLimit(pkg, limitMs)
                        editingAppItem = null
                    },
                    onCancelStagedLimitIncrease = { pkg ->
                        onCancelStagedLimitIncrease(pkg)
                        editingAppItem = null
                    }
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(
    state: LimitsUiState.Success,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (LimitsFilter) -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onOpenLimitEditor: (LimitsAppItem) -> Unit,
    onCancelStagedRemoval: (String) -> Unit,
    onClearNotice: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Commitment Lock Banner
        item(key = "commitment_lock_banner") {
            CommitmentLockBanner(
                isLocked = state.isCommitmentLocked,
                lockedNotice = state.lockedNotice,
                onDismissNotice = onClearNotice
            )
        }

        // Search Bar
        item(key = "search_bar") {
            SearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchQueryChange
            )
        }

        // Filter Controls
        item(key = "filter_chips") {
            FilterChipsRow(
                activeFilter = state.activeFilter,
                selectedCount = state.selectedCount,
                onFilterSelect = onFilterSelect
            )
        }

        // Empty Filter / Search Result State
        if (state.apps.isEmpty()) {
            item(key = "empty_filter_state") {
                EmptyFilterState(
                    searchQuery = state.searchQuery,
                    activeFilter = state.activeFilter,
                    onClearSearch = { onSearchQueryChange("") }
                )
            }
        } else {
            // App List Items
            items(
                items = state.apps,
                key = { it.packageName }
            ) { appItem ->
                AppListItemRow(
                    appItem = appItem,
                    isCommitmentLocked = state.isCommitmentLocked,
                    onToggleSelection = { onToggleAppSelection(appItem.packageName) },
                    onEditLimit = { onOpenLimitEditor(appItem) },
                    onCancelStagedRemoval = { onCancelStagedRemoval(appItem.packageName) }
                )
            }
        }
    }
}

@Composable
private fun CommitmentLockBanner(
    isLocked: Boolean,
    lockedNotice: String?,
    onDismissNotice: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLocked) {
                    MaterialTheme.extendedColors.commitmentContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = if (isLocked) {
                        "Commitment Lock is Active banner"
                    } else {
                        "Commitment Lock is Inactive banner"
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isLocked) "Commitment Lock Active" else "Commitment Lock Inactive",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isLocked) {
                                MaterialTheme.extendedColors.onCommitmentContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )

                        ProtectionStatusBadge(
                            text = if (isLocked) "Locked" else "Unlocked",
                            severity = if (isLocked) BadgeSeverity.COMMITMENT else BadgeSeverity.NEUTRAL
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isLocked) {
                            "Tightening limits takes effect immediately. Relaxing limits or removing protected apps will take effect at next midnight reset (12:00 AM)."
                        } else {
                            "Limit changes and app selection apply immediately. You can activate Commitment Lock anytime from the Home dashboard."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isLocked) {
                            MaterialTheme.extendedColors.onCommitmentContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        // Active Staged Action Notice Banner
        if (!lockedNotice.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.extendedColors.warningContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lockedNotice,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.extendedColors.onWarningContainer,
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(onClick = onDismissNotice) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.extendedColors.onWarningContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Search apps input" },
        placeholder = {
            Text(
                text = "Search by app or package name...",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        singleLine = true,
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.semantics { contentDescription = "Clear search query" }
                ) {
                    Text(
                        text = "✕",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun FilterChipsRow(
    activeFilter: LimitsFilter,
    selectedCount: Int,
    onFilterSelect: (LimitsFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // All Filter
        FilterChip(
            selected = activeFilter == LimitsFilter.ALL,
            onClick = { onFilterSelect(LimitsFilter.ALL) },
            label = { Text("All") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.semantics { contentDescription = "Filter All apps" }
        )

        // Controlled Filter
        FilterChip(
            selected = activeFilter == LimitsFilter.CONTROLLED,
            onClick = { onFilterSelect(LimitsFilter.CONTROLLED) },
            label = { Text("Controlled ($selectedCount)") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.semantics { contentDescription = "Filter Controlled apps ($selectedCount)" }
        )

        // Social & Video Filter
        FilterChip(
            selected = activeFilter == LimitsFilter.SOCIAL_VIDEO,
            onClick = { onFilterSelect(LimitsFilter.SOCIAL_VIDEO) },
            label = { Text("Social & Video") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.semantics { contentDescription = "Filter Social and Video apps" }
        )
    }
}

@Composable
private fun AppListItemRow(
    appItem: LimitsAppItem,
    isCommitmentLocked: Boolean,
    onToggleSelection: () -> Unit,
    onEditLimit: () -> Unit,
    onCancelStagedRemoval: () -> Unit
) {
    val rowDescription = buildString {
        append("${appItem.appLabel}, ")
        append(if (appItem.isSelected) "Controlled" else "Not controlled")
        append(", Today's usage: ${appItem.formattedUsage}, Limit: ${appItem.formattedLimit}")
        if (isCommitmentLocked) {
            append(", Commitment Lock active")
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row Header: Checkbox + Icon + Label + Edit Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = appItem.isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.semantics {
                        contentDescription = "Toggle monitoring for ${appItem.appLabel}"
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                AppIconThumbnail(
                    drawable = appItem.icon,
                    appName = appItem.appLabel,
                    packageName = appItem.packageName,
                    size = 44.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appItem.appLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = appItem.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Set / Edit Limit Action Button
                OutlinedButton(
                    onClick = onEditLimit,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .semantics { contentDescription = "Edit limit for ${appItem.appLabel}" }
                ) {
                    Text(
                        text = if (appItem.isSelected) "Edit limit" else "Set limit",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Staged Change Badges (Pending Removal / Pending Limit Increase)
            if (appItem.isPendingRemoval || appItem.pendingLimitIncreaseMs != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (appItem.isPendingRemoval) {
                        ProtectionStatusBadge(
                            text = "Removal at midnight",
                            severity = BadgeSeverity.WARNING
                        )
                        TextButton(
                            onClick = onCancelStagedRemoval,
                            modifier = Modifier.semantics {
                                contentDescription = "Cancel scheduled removal for ${appItem.appLabel}"
                            }
                        ) {
                            Text(
                                text = "Keep controlled",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (appItem.pendingLimitIncreaseMs != null) {
                        ProtectionStatusBadge(
                            text = "New limit at midnight: ${appItem.formattedPendingLimit ?: ""}",
                            severity = BadgeSeverity.WARNING
                        )
                    }
                }
            }

            // Usage & Limit Information Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today: ${appItem.formattedUsage}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (appItem.isSelected) "Limit: ${appItem.formattedLimit}" else "No limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (appItem.isSelected) {
                    Text(
                        text = "Remaining: ${appItem.formattedRemaining}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (appItem.remainingDurationMs <= 0L) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            // Progress Bar (visually capped at 100%, with commitment message on exceed)
            UsageProgressBar(
                usedMs = appItem.totalTimeInForegroundMs,
                limitMs = if (appItem.isSelected) appItem.limitDurationMs else null,
                showPercent = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EmptyFilterState(
    searchQuery: String,
    activeFilter: LimitsFilter,
    onClearSearch: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .semantics { contentDescription = "Empty search or filter state" }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val emptyTitle = when {
                searchQuery.isNotEmpty() -> "No matching apps found"
                activeFilter == LimitsFilter.CONTROLLED -> "No controlled applications"
                activeFilter == LimitsFilter.SOCIAL_VIDEO -> "No Social & Video apps detected"
                else -> "No applications available"
            }

            val emptyMessage = when {
                searchQuery.isNotEmpty() -> "No installed applications matched \"$searchQuery\"."
                activeFilter == LimitsFilter.CONTROLLED -> "You haven't selected any apps to monitor yet. Switch to 'All' to choose apps to protect yourself from."
                activeFilter == LimitsFilter.SOCIAL_VIDEO -> "No applications on this device were classified as Social or Video by Android system metadata."
                else -> "No applications discovered."
            }

            Text(
                text = emptyTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (searchQuery.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onClearSearch) {
                    Text("Clear search query")
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Loading applications and limits" },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = "Discovering installed applications...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRequiredState(onRequestUsagePermission: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Usage Access Required",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Stop Doom Scroll requires Usage Access permission to query foreground screen time and evaluate daily limit rules.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (onRequestUsagePermission != null) {
                    Button(
                        onClick = onRequestUsagePermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Grant Usage Access",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorState(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Unable to Load Limits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun EmptyCatalogState(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "No Applications Discovered",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "No launchable third-party or user applications were found on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh")
                }
            }
        }
    }
}

package com.scrollstop.ui.screens.accessibility

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scrollstop.R
import com.scrollstop.domain.model.AccessibilityServiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityConsentScreen(
    uiState: AccessibilityConsentUiState,
    onGrantConsent: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.accessibility_disclosure_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .semantics {
                                contentDescription = "Back Button"
                            }
                    ) {
                        Text(text = stringResource(id = R.string.btn_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // Service Health Card
                val statusText = when (uiState.serviceState) {
                    AccessibilityServiceState.ACCESSIBILITY_ACTIVE ->
                        stringResource(id = R.string.accessibility_status_active)
                    AccessibilityServiceState.ACCESSIBILITY_INTERRUPTED ->
                        stringResource(id = R.string.accessibility_status_interrupted)
                    AccessibilityServiceState.ACCESSIBILITY_NOT_GRANTED ->
                        stringResource(id = R.string.accessibility_status_not_granted)
                }

                val statusColor = when (uiState.serviceState) {
                    AccessibilityServiceState.ACCESSIBILITY_ACTIVE -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Accessibility Service Health Status: $statusText"
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = statusColor.copy(alpha = 0.1f)
                    ),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status: ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Prominent Disclosure Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Prominent Accessibility Disclosure Details"
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.accessibility_disclosure_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Grant Consent Action Button
                Button(
                    onClick = onGrantConsent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .semantics {
                            contentDescription = "Grant Accessibility Permission Button"
                        }
                ) {
                    Text(
                        text = stringResource(id = R.string.btn_grant_accessibility_consent),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Internal Trigger Information (if present for validation)
                if (uiState.latestTrigger != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = "Internal Enforcement Trigger Detection Info"
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Deterministic Enforcement Trigger Emitted",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    id = R.string.trigger_received_message,
                                    uiState.latestTrigger.packageName
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Development Diagnostic Panel (POC-04 Event Chain Debug)
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Development Diagnostic Panel"
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "DEV DIAGNOSTICS (POC-04 EVENT TRACE)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Accessibility Service: ${uiState.serviceState}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Last Event Package: ${uiState.diagnosticInfo.lastEventPackage ?: "None"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Last Detection: ${uiState.diagnosticInfo.lastDetectionPackage ?: "None"} (selected = ${uiState.diagnosticInfo.lastDetectionSelected ?: "N/A"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Last Rule Evaluation: ${uiState.diagnosticInfo.lastRulePackage ?: "None"} (${uiState.diagnosticInfo.lastRuleState ?: "N/A"})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Last Enforcement Trigger: ${uiState.latestTrigger?.packageName ?: "None"} (${uiState.latestTrigger?.reason ?: "No Trigger"})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.latestTrigger != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

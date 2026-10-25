package com.localdev.studiox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdev.studiox.ui.AppScreen
import com.localdev.studiox.ui.MainViewModel
import com.localdev.studiox.ui.components.ServiceStatusBadge
import com.localdev.studiox.ui.screens.AboutLicensesScreen
import com.localdev.studiox.ui.screens.CodeEditorScreen
import com.localdev.studiox.ui.screens.DashboardScreen
import com.localdev.studiox.ui.screens.DatabaseManagerScreen
import com.localdev.studiox.ui.screens.DiagnosticsScreen
import com.localdev.studiox.ui.screens.FileExplorerScreen
import com.localdev.studiox.ui.screens.LogsViewerScreen
import com.localdev.studiox.ui.screens.NetworkPortsScreen
import com.localdev.studiox.ui.screens.PhpRuntimeScreen
import com.localdev.studiox.ui.screens.ProjectsScreen
import com.localdev.studiox.ui.screens.ServerControlPanelScreen
import com.localdev.studiox.ui.screens.SettingsScreen
import com.localdev.studiox.ui.theme.DevAccentCyan
import com.localdev.studiox.ui.theme.DevBackground
import com.localdev.studiox.ui.theme.DevSurfacePrimary
import com.localdev.studiox.ui.theme.DevSurfaceSecondary
import com.localdev.studiox.ui.theme.DevTextPrimary
import com.localdev.studiox.ui.theme.DevTextSecondary
import com.localdev.studiox.ui.theme.LocalDevStudioXTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalDevStudioXTheme {
                val state by viewModel.uiState.collectAsState()
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DevBackground),
                    topBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DevSurfacePrimary)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "LOCALDEV STUDIO X",
                                        color = DevAccentCyan,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "http://${state.bindHost}:${state.port}/ • ${state.currentScreen.title}",
                                        color = DevTextSecondary,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                ServiceStatusBadge(state.httpState)
                            }

                            if (state.statusBannerMessage.isNotBlank()) {
                                Text(
                                    text = state.statusBannerMessage,
                                    color = DevAccentCyan,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            // Scrollable Navigation Bar for all 12 Developer Screens
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (screen in AppScreen.entries) {
                                    val selected = state.currentScreen == screen
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.navigateTo(screen) },
                                        label = { Text(screen.title, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = DevAccentCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = DevAccentCyan,
                                            labelColor = DevTextPrimary
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DevBackground)
                            .padding(innerPadding)
                    ) {
                        when (state.currentScreen) {
                            AppScreen.DASHBOARD -> DashboardScreen(state, viewModel)
                            AppScreen.SERVER_MANAGER -> ServerControlPanelScreen(state, viewModel)
                            AppScreen.PHP_RUNTIME -> PhpRuntimeScreen(state, viewModel)
                            AppScreen.DATABASE_MANAGER -> DatabaseManagerScreen(state, viewModel)
                            AppScreen.PROJECTS -> ProjectsScreen(state, viewModel)
                            AppScreen.FILE_EXPLORER -> FileExplorerScreen(state, viewModel)
                            AppScreen.CODE_EDITOR -> CodeEditorScreen(state, viewModel)
                            AppScreen.LOGS_VIEWER -> LogsViewerScreen(state, viewModel)
                            AppScreen.NETWORK_PORTS -> NetworkPortsScreen(state, viewModel)
                            AppScreen.DIAGNOSTICS -> DiagnosticsScreen(state, viewModel)
                            AppScreen.SETTINGS -> SettingsScreen(state, viewModel)
                            AppScreen.ABOUT -> AboutLicensesScreen()
                        }
                    }
                }
            }
        }
    }
}
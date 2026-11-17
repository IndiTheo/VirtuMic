package com.indidevs.virtumic.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.indidevs.virtumic.ui.feature.settings.SettingsScreen
import com.indidevs.virtumic.ui.feature.start.StartScreen

enum class Section(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val contentDescription: String
) {
    START("start", "Start", Icons.Default.Home, "Start"),
    SETTINGS("settings", "Settings", Icons.Default.Settings, "Settings")
}

@Composable
fun AppScaffold(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val startSection = Section.START
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Row(modifier = modifier.fillMaxSize()) {
        if (isLandscape) {
            SideNavBar(navController, startSection)
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                if (!isLandscape) {
                    BottomNavBar(navController, startSection)
                }
            }
        ) { contentPadding ->
            AppNavHost(
                navController = navController,
                startSection = startSection,
                modifier = Modifier.padding(contentPadding)
            )
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    startSection: Section,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startSection.route,
        modifier = modifier
    ) {
        composable(Section.START.route) {
            StartScreen()
        }
        composable(Section.SETTINGS.route) {
            SettingsScreen()
        }
    }
}

@Composable
private fun SideNavBar(navController: NavHostController, startSection: Section) {
    var selectedSection by rememberSaveable { mutableIntStateOf(startSection.ordinal) }

    NavigationRail(
        windowInsets = WindowInsets.statusBars
    ) {
        Section.entries.forEachIndexed { index, section ->
            NavigationRailItem(
                selected = selectedSection == index,
                onClick = {
                    navController.navigate(route = section.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                    selectedSection = index
                },
                icon = {
                    Icon(
                        section.icon,
                        contentDescription = section.contentDescription
                    )
                },
                label = { Text(section.label) }
            )
        }
    }
}

@Composable
private fun BottomNavBar(navController: NavHostController, startSection: Section) {
    var selectedSection by rememberSaveable { mutableIntStateOf(startSection.ordinal) }

    NavigationBar(
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Section.entries.forEachIndexed { index, section ->
            NavigationBarItem(
                selected = selectedSection == index,
                onClick = {
                    navController.navigate(route = section.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                    selectedSection = index
                },
                icon = {
                    Icon(
                        section.icon,
                        contentDescription = section.contentDescription
                    )
                },
                label = { Text(section.label) }
            )
        }
    }
}

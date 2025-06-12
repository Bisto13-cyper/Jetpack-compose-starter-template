package com.startup.template.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import com.startup.template.presentation.details.DetailsScreen
import com.startup.template.presentation.home.HomeScreen


@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(HomeRoute)

    NavDisplay(
        entryDecorators = listOf(
            rememberSceneSetupNavEntryDecorator(),
            rememberSavedStateNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    onNavigateToDetails = {
                        backStack.add(DetailsRoute("Testing parameter"))
                    }
                )
            }
            entry<DetailsRoute> {
                DetailsScreen(
                    parameter = it.parameter
                )
            }
        }
    )
}
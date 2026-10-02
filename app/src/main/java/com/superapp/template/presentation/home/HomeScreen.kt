package com.startup.template.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.startup.template.ui.theme.JetpackStartupTemplateTheme

@Composable
fun HomeScreen(
    onNavigateToDetails: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold {
        Column(
            modifier = Modifier.padding(it)
        ) {
            ElevatedButton(
                onClick = onNavigateToDetails
            ) {
                Text("Go to Details Screen")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    JetpackStartupTemplateTheme {
        HomeScreen(
            onNavigateToDetails = {}

        )
    }
}
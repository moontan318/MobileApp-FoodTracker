package com.mealmacros.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mealmacros.app.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeImportScreen(vm: AppViewModel, initialUrl: String) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    val state = vm.importState
    val canImport = url.isNotBlank() && !state.loading

    // A link shared from another app imports straight away.
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && !vm.importState.loading) vm.importRecipe(initialUrl)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import a recipe") },
                navigationIcon = {
                    IconButton(onClick = { vm.clearImportError(); vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.trim(); vm.clearImportError() },
                label = { Text("Recipe web address") },
                placeholder = { Text("https://www.bbc.co.uk/food/recipes/…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (canImport) vm.importRecipe(url) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.importRecipe(url) }, enabled = canImport, modifier = Modifier.fillMaxWidth()) {
                if (state.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Importing…", Modifier.padding(start = 8.dp))
                    }
                } else {
                    Text("Import")
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "The ingredients are matched to foods and weights from the database. You'll then see the " +
                    "recipe to check: change any food or weight so it matches exactly what you cooked, then save.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Works with most recipe sites that publish recipe details for search engines, such as BBC Food, " +
                    "BBC Good Food and delicious. magazine. Tip: in your browser, use Share → Meal Macros to " +
                    "import the page you're reading.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

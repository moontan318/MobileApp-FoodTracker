package com.mealmacros.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mealmacros.app.ui.CustomFoodScreen
import com.mealmacros.app.ui.FoodSearchScreen
import com.mealmacros.app.ui.HomeScreen
import com.mealmacros.app.ui.MealMacrosTheme
import com.mealmacros.app.ui.RecipeDetailScreen
import com.mealmacros.app.ui.RecipeEditorScreen
import com.mealmacros.app.ui.RecipeImportScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            MealMacrosTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A web page shared from a browser ("Share → Meal Macros") opens the recipe import. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = Regex("""https?://\S+""").find(text)?.value ?: return
        vm.navigate(Screen.RecipeImport(url))
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    if (!vm.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }
    when (val screen = vm.screen) {
        Screen.Home -> HomeScreen(vm)
        is Screen.RecipeDetail -> RecipeDetailScreen(vm, screen.recipeId)
        Screen.RecipeEditor -> RecipeEditorScreen(vm)
        is Screen.FoodSearch -> FoodSearchScreen(vm, screen.target)
        is Screen.CustomFoodEditor -> CustomFoodScreen(vm, screen.foodId)
        is Screen.RecipeImport -> RecipeImportScreen(vm, screen.url)
    }
}

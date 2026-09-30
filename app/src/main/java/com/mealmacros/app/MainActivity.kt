package com.mealmacros.app

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

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MealMacrosTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot(vm)
                }
            }
        }
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
        Screen.FoodSearch -> FoodSearchScreen(vm)
        is Screen.CustomFoodEditor -> CustomFoodScreen(vm, screen.foodId)
    }
}

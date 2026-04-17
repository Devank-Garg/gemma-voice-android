package com.example.gemmaapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gemmaapp.ui.chat.ChatScreen
import com.example.gemmaapp.ui.home.HomeScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartChat = {
                    navController.navigate(Screen.Chat.route)
                }
            )
        }
        composable(Screen.Chat.route) {
            ChatScreen()
        }
    }
}

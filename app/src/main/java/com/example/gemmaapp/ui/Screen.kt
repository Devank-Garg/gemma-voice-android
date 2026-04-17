package com.example.gemmaapp.ui

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat")
}

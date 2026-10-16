package com.youtubeclone.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.youtubeclone.app.presentation.navigation.AppRoot
import com.youtubeclone.app.presentation.theme.YoutubeCloneTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Écran de démarrage natif à fond sombre (Theme.App.Starting), avant la première image de Compose.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Thème toujours sombre : icônes claires dans les barres système, quel que soit le réglage du téléphone.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            YoutubeCloneTheme {
                AppRoot()
            }
        }
    }
}

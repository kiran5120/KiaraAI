package com.kiara.ai

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kiara.ai.ui.AppBackground
import com.kiara.ai.ui.ChatScreen
import com.kiara.ai.ui.CodeScreen
import com.kiara.ai.ui.HomeScreen
import com.kiara.ai.ui.KiaraTheme
import com.kiara.ai.ui.LocalPalette
import com.kiara.ai.ui.SettingsScreen
import com.kiara.ai.ui.ToolsScreen
import com.kiara.ai.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsState()
            KiaraTheme(settings.theme) {
                val ctx = LocalContext.current
                val pal = LocalPalette.current
                var tab by rememberSaveable { mutableIntStateOf(0) }

                LaunchedEffect(Unit) {
                    vm.events.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
                }

                AppBackground {
                    Scaffold(
                        containerColor = Color.Transparent,
                        bottomBar = {
                            NavigationBar(containerColor = Color(0xCC0F0A26)) {
                                val items = listOf(
                                    "Home" to Icons.Filled.Home,
                                    "Chat" to Icons.Filled.Chat,
                                    "Code" to Icons.Filled.Code,
                                    "Tools" to Icons.Filled.Build,
                                    "Settings" to Icons.Filled.Settings
                                )
                                items.forEachIndexed { i, (name, icon) ->
                                    NavigationBarItem(
                                        selected = tab == i,
                                        onClick = { tab = i },
                                        icon = { Icon(icon, name) },
                                        label = { Text(name) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color.Black,
                                            indicatorColor = pal.primary,
                                            unselectedIconColor = Color(0xFFB9ADDB),
                                            selectedTextColor = pal.primary,
                                            unselectedTextColor = Color(0xFFB9ADDB)
                                        )
                                    )
                                }
                            }
                        }
                    ) { inner ->
                        val m = Modifier.padding(inner)
                        androidx.compose.foundation.layout.Box(m) {
                            when (tab) {
                                0 -> HomeScreen(vm) { tab = 1 }
                                1 -> ChatScreen(vm)
                                2 -> CodeScreen(vm)
                                3 -> ToolsScreen(vm)
                                else -> SettingsScreen(vm)
                            }
                        }
                    }
                }
            }
        }
    }
}

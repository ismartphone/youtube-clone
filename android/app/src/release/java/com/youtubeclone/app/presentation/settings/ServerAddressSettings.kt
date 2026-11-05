package com.youtubeclone.app.presentation.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import com.youtubeclone.app.presentation.common.UiError

// Release (ADR-027) : le réglage de l'adresse du serveur n'existe pas. Ces points d'entrée sont
// vides ; leur version debug se trouve dans src/debug.

@Composable
@Suppress("UNUSED_PARAMETER")
fun ServerAddressSettingsRow(onClick: () -> Unit) = Unit

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.serverAddressDestination(navController: NavController) = Unit

fun NavController.openServerAddress() = Unit

@Composable
@Suppress("UNUSED_PARAMETER")
fun ServerAddressLink(error: UiError) = Unit

@Composable
@Suppress("UNUSED_PARAMETER")
fun ProvideServerAddressLink(onOpen: () -> Unit, content: @Composable () -> Unit) = content()

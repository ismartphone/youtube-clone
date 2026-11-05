package com.youtubeclone.app.data.server

import android.content.Context
import com.youtubeclone.app.BuildConfig

/**
 * Adresse de l'API (ADR-027). En release, c'est toujours la constante de compilation : aucun
 * réglage, aucun stockage. La version debug (src/debug) la remplace par l'adresse réglée.
 */
object ServerAddress {
    val baseUrl: String = BuildConfig.API_BASE_URL

    @Suppress("UNUSED_PARAMETER")
    fun initialize(context: Context) = Unit
}

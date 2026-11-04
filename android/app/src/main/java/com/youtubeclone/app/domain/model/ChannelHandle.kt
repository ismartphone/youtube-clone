package com.youtubeclone.app.domain.model

import java.text.Normalizer

// Identifiant d'une chaîne (@handle), avec la même règle que le serveur : 3 à 30 caractères,
// lettres minuscules, chiffres, point, tiret et tiret bas.

/** Photo de profil ou bannière de la chaîne (ADR-018). */
enum class ChannelImageKind { Photo, Banner }

/** Photo et bannière : 5 Mo au plus, comme le serveur. */
const val MAX_CHANNEL_IMAGE_BYTES = 5L * 1024 * 1024

const val HANDLE_MIN_LENGTH = 3
const val HANDLE_MAX_LENGTH = 30

private val HANDLE_PATTERN = Regex("^[a-z0-9._-]{$HANDLE_MIN_LENGTH,$HANDLE_MAX_LENGTH}$")
private val DIACRITICS = Regex("""\p{M}+""")
private val NOT_ALLOWED = Regex("[^a-z0-9._-]")

fun isValidHandle(handle: String): Boolean = HANDLE_PATTERN.matches(handle)

/** Handle proposé à partir du nom : « Carnet de Route » donne « carnetderoute », « Été » donne « ete ». */
fun suggestHandle(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase()
        .replace(NOT_ALLOWED, "")
        .take(HANDLE_MAX_LENGTH)

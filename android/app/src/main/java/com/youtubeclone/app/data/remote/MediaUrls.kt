package com.youtubeclone.app.data.remote

import com.youtubeclone.app.data.server.ServerAddress

/**
 * Les fichiers stockés chez nous (vidéos, miniatures générées) sont enregistrés avec un chemin relatif,
 * par exemple /media/videos/<nom>.mp4 : on le complète avec l'adresse de l'API, la même que
 * celle utilisée par l'application (10.0.2.2 sur l'émulateur). Une URL complète est gardée telle quelle.
 */
internal fun resolveMediaUrl(url: String): String =
    if (url.startsWith("/")) ServerAddress.baseUrl.trimEnd('/') + url else url

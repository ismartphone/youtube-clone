package com.youtubeclone.app.domain.model

/** Erreurs d'accès aux données, indépendantes de la bibliothèque réseau utilisée. */
sealed class DataError : Exception() {
    /** Le serveur est injoignable : pas de réseau, délai dépassé, serveur arrêté. */
    data object Network : DataError() {
        private fun readResolve(): Any = Network
    }

    /** Le serveur a répondu avec un code d'erreur HTTP, et parfois un message destiné à l'utilisateur. */
    data class Server(val code: Int, val serverMessage: String? = null) : DataError()

    /** La ressource demandée n'existe pas. */
    data object NotFound : DataError() {
        private fun readResolve(): Any = NotFound
    }

    /** Vidéo exclusive demandée sans abonnement Premium en cours : le serveur refuse l'accès (ADR-007). */
    data object PremiumRequired : DataError() {
        private fun readResolve(): Any = PremiumRequired
    }
}

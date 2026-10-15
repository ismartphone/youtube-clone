package com.youtubeclone.app.domain.model

/** Erreurs d'accès aux données, indépendantes de la bibliothèque réseau utilisée. */
sealed class DataError : Exception() {
    /** Le serveur est injoignable : pas de réseau, délai dépassé, serveur arrêté. */
    data object Network : DataError() {
        private fun readResolve(): Any = Network
    }

    /** Le serveur a répondu avec un code d'erreur HTTP. */
    data class Server(val code: Int) : DataError()

    /** La ressource demandée n'existe pas. */
    data object NotFound : DataError() {
        private fun readResolve(): Any = NotFound
    }
}

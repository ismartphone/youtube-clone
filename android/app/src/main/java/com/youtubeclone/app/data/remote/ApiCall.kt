package com.youtubeclone.app.data.remote

import com.youtubeclone.app.domain.model.DataError
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * Exécute un appel à l'API et traduit les exceptions réseau en erreurs du domaine.
 * Le message d'erreur renvoyé par NestJS est conservé pour être affiché à l'utilisateur.
 */
internal suspend fun <T> apiCall(json: Json, block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw if (e.code() == 404) DataError.NotFound else DataError.Server(e.code(), e.serverMessage(json))
    } catch (e: IOException) {
        throw DataError.Network
    }

// Corps d'erreur NestJS : { "statusCode": 409, "message": "...", "error": "Conflict" }.
// "message" est une liste quand plusieurs champs sont refusés par la validation : on garde le premier.
private fun HttpException.serverMessage(json: Json): String? = runCatching {
    val body = response()?.errorBody()?.string() ?: return null
    when (val message = json.parseToJsonElement(body).jsonObject["message"]) {
        is JsonPrimitive -> message.contentOrNull
        is JsonArray -> message.firstOrNull()?.jsonPrimitive?.contentOrNull
        else -> null
    }
}.getOrNull()

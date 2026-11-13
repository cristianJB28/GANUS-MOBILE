package com.ganus.mobile.core.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import java.io.IOException

private val errorJson = Json

/**
 * Texto para mostrar cuando la API rechaza una llamada, tanto en el formato documentado como en el formato
 * estándar de validación de NestJS (`message` como lista).
 *
 * - 401: la sesión venció (el token dura 30 días): hay que iniciar sesión de nuevo.
 * - 422: el servidor rechazó los datos y dice por qué; reenviarlos iguales no sirve.
 * - 5xx y sin red: se puede reintentar luego.
 */
fun Throwable.mensajeParaUsuario(porDefecto: String = "error desconocido"): String {
    if (this !is HttpException) return localizedMessage ?: porDefecto
    val cuerpo = try {
        response()?.errorBody()?.string()
    } catch (exception: IOException) {
        return "Error ${code()} del servidor; no se pudo leer el detalle: ${exception.localizedMessage ?: "fallo de lectura"}."
    }
    val error = cuerpo?.let(::parseErrorBody)
    val mensajes = error?.let(::mensajesDeError).orEmpty()
    return when (code()) {
        401 -> "Tu sesión venció. Inicia sesión de nuevo."
        422 -> mensajes.distinct().joinToString(" ")
            .ifBlank { cuerpo?.mensajeVisible() ?: "El servidor rechazó los datos (422) sin indicar el motivo." }
        in 500..599 -> "El servidor no está disponible por ahora. Se reintentará al sincronizar."
        else -> mensajes.firstOrNull() ?: "Error ${code()} del servidor."
    }
}

private fun parseErrorBody(cuerpo: String): JsonElement? =
    try {
        errorJson.parseToJsonElement(cuerpo)
    } catch (_: SerializationException) {
        null
    }

private fun mensajesDeError(error: JsonElement): List<String> =
    when (error) {
        is JsonPrimitive -> error.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?.let(::listOf)
            .orEmpty()
        is JsonArray -> error.flatMap(::mensajesDeError)
        is JsonObject -> listOf("mensaje", "message", "detalles", "details", "detail")
            .flatMap { clave -> error[clave]?.let(::mensajesDeError).orEmpty() }
        else -> emptyList()
    }

private fun String.mensajeVisible(): String =
    filterNot(Char::isISOControl)
        .trim()
        .take(240)

package pe.edu.upeu.pharmamobil.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pe.edu.upeu.pharmamobil.data.local.AlmacenSeguro
import pe.edu.upeu.pharmamobil.platform.URL_BASE

/** Lector JSON compartido: PharmaSoft envia campos que la app no usa. */
internal val JsonPharma = Json { ignoreUnknownKeys = true }

/**
 * Unico HttpClient del proyecto. Los plugins se configuran aqui una sola vez
 * y se aplican a todas las peticiones: ningun repositorio escribe la cabecera
 * Authorization a mano.
 */
fun crearHttpClient(
    motor: HttpClientEngine,
    almacen: AlmacenSeguro,
    esDepuracion: Boolean,
    registro: Logger = Logger.SIMPLE
): HttpClient = HttpClient(motor) {

    // Un 4xx o 5xx se lanza como ResponseException y lo traduce llamadaApi.
    expectSuccess = true

    // Sin límite, una red lenta dejaría la sincronización colgada (Guía 13).
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        requestTimeoutMillis = 15_000
    }

    install(ContentNegotiation) {
        json(JsonPharma)
    }

    install(DefaultRequest) {
        url(URL_BASE)
        contentType(ContentType.Application.Json)
    }

    install(Auth) {
        bearer {
            loadTokens {
                almacen.leerToken()?.let { BearerTokens(it, "") }
            }
            sendWithoutRequest { peticion ->
                !peticion.url.encodedPath.endsWith("/auth/login")
            }
        }
    }

    install(Logging) {
        // Logger.SIMPLE escribe con println: en Android aparece en Logcat.
        logger = registro
        level = if (esDepuracion) LogLevel.HEADERS else LogLevel.NONE
        sanitizeHeader { nombre -> nombre == HttpHeaders.Authorization }
    }
}

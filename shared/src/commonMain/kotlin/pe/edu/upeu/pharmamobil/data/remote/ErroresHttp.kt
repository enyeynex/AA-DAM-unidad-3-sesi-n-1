package pe.edu.upeu.pharmamobil.data.remote

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.io.IOException
import pe.edu.upeu.pharmamobil.data.remote.dto.ErrorDto
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.sesion.AvisosDeSesion
import kotlin.coroutines.cancellation.CancellationException

/** Unico lugar donde un codigo HTTP se convierte en un error de sesion. */
fun mapearErrorSesion(estado: HttpStatusCode, mensaje: String?) =
    when (estado) {
        HttpStatusCode.Unauthorized ->
            if (mensaje?.contains("expirado") == true)
                ErrorSesion.SesionExpirada
            else ErrorSesion.CredencialesInvalidas
        HttpStatusCode.Forbidden -> ErrorSesion.SinPermiso
        else -> ErrorSesion.SinConexion
    }

/**
 * Error del servidor que no es de sesion (validacion, conflicto, 500...).
 * [codigo] permite a la sincronizacion distinguir un fallo transitorio (5xx)
 * de uno permanente (400, 404, 409).
 */
class ErrorServidorException(mensaje: String, val codigo: Int = 0) : Exception(mensaje)

/**
 * Ejecuta una llamada al servidor y traduce sus fallos:
 * - 401 y 403 pasan a [FalloSesionException]; un 401 fuera del login ademas
 *   se avisa por [AvisosDeSesion] para que la app vuelva al login.
 * - Un fallo de red pasa a [ErrorSesion.SinConexion].
 * - Cualquier otro codigo conserva el mensaje que envio PharmaSoft.
 */
internal suspend fun <T> llamadaApi(
    avisos: AvisosDeSesion?,
    bloque: suspend () -> T
): T {
    try {
        return bloque()
    } catch (cancelacion: CancellationException) {
        throw cancelacion
    } catch (fallo: ResponseException) {
        val estado = fallo.response.status
        val error = leerError(fallo)

        if (estado == HttpStatusCode.Unauthorized || estado == HttpStatusCode.Forbidden) {
            val errorSesion = mapearErrorSesion(estado, error?.message)
            if (estado == HttpStatusCode.Unauthorized) {
                avisos?.notificar(errorSesion, error?.message)
            }
            throw FalloSesionException(errorSesion, error?.message)
        }

        val detalle = error?.validationErrors?.values?.firstOrNull()
        throw ErrorServidorException(
            detalle ?: error?.message ?: "PharmaSoft respondió ${estado.value}",
            estado.value
        )
    } catch (tiempo: HttpRequestTimeoutException) {
        throw FalloSesionException(ErrorSesion.TiempoAgotado)
    } catch (fallo: IOException) {
        throw FalloSesionException(ErrorSesion.SinConexion)
    }
}

private suspend fun leerError(fallo: ResponseException): ErrorDto? =
    try {
        JsonPharma.decodeFromString(ErrorDto.serializer(), fallo.response.bodyAsText())
    } catch (cancelacion: CancellationException) {
        throw cancelacion
    } catch (otro: Exception) {
        null
    }

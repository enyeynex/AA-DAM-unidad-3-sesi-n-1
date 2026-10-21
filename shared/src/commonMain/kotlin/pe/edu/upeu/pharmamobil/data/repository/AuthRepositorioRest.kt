package pe.edu.upeu.pharmamobil.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import pe.edu.upeu.pharmamobil.data.local.AlmacenSeguro
import pe.edu.upeu.pharmamobil.data.remote.dto.LoginRequestDto
import pe.edu.upeu.pharmamobil.data.remote.dto.LoginResponseDto
import pe.edu.upeu.pharmamobil.data.remote.dto.UsuarioDto
import pe.edu.upeu.pharmamobil.data.remote.dto.aSesion
import pe.edu.upeu.pharmamobil.data.remote.leerClaims
import pe.edu.upeu.pharmamobil.data.remote.llamadaApi
import pe.edu.upeu.pharmamobil.domain.model.Credenciales
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.model.Sesion
import pe.edu.upeu.pharmamobil.domain.repository.AuthRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private fun segundosActuales(): Long = Clock.System.now().epochSeconds

class AuthRepositorioRest(
    private val cliente: HttpClient,
    private val almacen: AlmacenSeguro,
    private val ahora: () -> Long = ::segundosActuales
) : AuthRepository {

    override suspend fun iniciarSesion(c: Credenciales): Sesion {
        val dto: LoginResponseDto = llamadaApi(avisos = null) {
            cliente.post("auth/login") {
                setBody(LoginRequestDto(c.usuario, c.clave))
            }.body()
        }
        almacen.guardarToken(dto.token)
        // loadTokens quedo en cache con el valor anterior: se fuerza a releerlo.
        cliente.authProvider<BearerAuthProvider>()?.clearToken()
        return dto.aSesion()
    }

    /**
     * Reconstruye la sesion a partir del token del almacen pidiendo /auth/me,
     * para tener el nombre y los permisos actuales.
     *
     * Offline-first (actividad autonoma de la sesion 11): un token vencido ya
     * no se descarta sin preguntar. Con red, PharmaSoft responde 401 y se
     * cierra la sesion. Sin red, mandar al login no sirve (no hay con que
     * iniciar sesion): la sesion se arma con los claims del token para que el
     * usuario vea sus datos locales, y el 401 de la siguiente sincronizacion
     * la cierra en cuanto vuelva la conexion.
     */
    override suspend fun sesionGuardada(): Sesion? {
        val token = almacen.leerToken() ?: return null
        val claims = leerClaims(token)

        if (claims == null) {
            cerrarSesion()
            return null
        }

        return try {
            val usuario: UsuarioDto = llamadaApi(avisos = null) {
                cliente.get("auth/me").body()
            }
            usuario.aSesion(token)
        } catch (fallo: FalloSesionException) {
            when (fallo.error) {
                ErrorSesion.SinConexion -> Sesion(
                    token = token,
                    usuario = claims.usuario,
                    nombreCompleto = claims.usuario,
                    permisos = claims.autoridades.toSet(),
                    expirada = claims.expirado(ahora())
                )
                else -> {
                    cerrarSesion()
                    null
                }
            }
        }
    }

    override suspend fun cerrarSesion() {
        almacen.borrarToken()
        cliente.authProvider<BearerAuthProvider>()?.clearToken()
    }
}

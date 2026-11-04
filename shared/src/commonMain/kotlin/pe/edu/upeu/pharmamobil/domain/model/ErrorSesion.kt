package pe.edu.upeu.pharmamobil.domain.model

/** Lo que puede salir mal con una sesion, sin hablar de codigos HTTP. */
sealed interface ErrorSesion {
    data object CredencialesInvalidas : ErrorSesion
    data object SesionExpirada : ErrorSesion
    data object SinPermiso : ErrorSesion
    data object SinConexion : ErrorSesion
    /** La petición salió pero el servicio no respondió a tiempo (Guía 13). */
    data object TiempoAgotado : ErrorSesion
}

/**
 * Transporta un [ErrorSesion] a traves de Result. El mensaje es el que ve el
 * usuario, por eso se redacta aqui y no en cada pantalla.
 */
class FalloSesionException(
    val error: ErrorSesion,
    /** Mensaje literal del servidor (campo message), si lo envio. */
    val detalle: String? = null
) : Exception(mensajeDe(error))

fun mensajeDe(error: ErrorSesion): String = when (error) {
    ErrorSesion.CredencialesInvalidas -> "Usuario o contraseña incorrectos"
    ErrorSesion.SesionExpirada -> "Tu sesión expiró. Vuelve a iniciar sesión"
    ErrorSesion.SinPermiso -> "No tienes permiso para realizar esta acción"
    ErrorSesion.SinConexion -> "No se pudo conectar con PharmaSoft. Revisa que el servidor esté en ejecución"
    ErrorSesion.TiempoAgotado -> "PharmaSoft tardó demasiado en responder. Se reintentará"
}

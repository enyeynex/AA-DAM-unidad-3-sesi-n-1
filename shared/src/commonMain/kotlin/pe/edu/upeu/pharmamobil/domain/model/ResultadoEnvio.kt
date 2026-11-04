package pe.edu.upeu.pharmamobil.domain.model

/**
 * Resultado de enviar una fila pendiente (Guía 13). El dominio no conoce
 * códigos HTTP: la capa de datos traduce cada error a una de las tres ramas.
 */
sealed interface ResultadoEnvio {

    /** PharmaSoft aceptó la fila y devolvió su versión con el id definitivo. */
    data class Exito(val delServidor: Producto) : ResultadoEnvio {
        val idServidor: Long get() = delServidor.id
    }

    /**
     * Puede funcionar más tarde: tiempo agotado o servicio no disponible (5xx).
     * [sinConexion] indica que la petición ni siquiera salió del teléfono.
     */
    data class Transitorio(val motivo: String, val sinConexion: Boolean = false) : ResultadoEnvio

    /** Reintentarlo no cambia nada: validación, conflicto, recurso inexistente o sin privilegio. */
    data class Permanente(val motivo: String) : ResultadoEnvio
}

/** Lo que dejó una sincronización: se muestra al usuario. */
data class ResumenSync(
    val enviadas: Int = 0,
    val fallidas: Int = 0,
    val pendientes: Int = 0,
    /** Filas enviadas en esta sincronización: id local → versión del servidor. */
    val confirmados: Map<Long, Producto> = emptyMap()
)

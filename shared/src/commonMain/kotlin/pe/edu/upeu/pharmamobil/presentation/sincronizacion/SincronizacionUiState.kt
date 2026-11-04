package pe.edu.upeu.pharmamobil.presentation.sincronizacion

/**
 * Lo que el usuario debe saber sin abrir ningún menú (Guía 13): si hay
 * conexión, cuántos cambios faltan enviar, cuáles fallaron y de cuándo son
 * los datos. Se arma solo con consultas observables de la base local y el
 * monitor de red, nunca consultando el servicio.
 */
data class SincronizacionUiState(
    val enCurso: Boolean = false,
    val pendientes: Int = 0,
    val fallidas: Int = 0,
    /** Epoch en milisegundos de la última sincronización correcta. */
    val ultimaSync: Long? = null,
    val conectado: Boolean = true
) {

    /** «Sin conexión · 3 cambios por enviar», «2 cambios por enviar» o null. */
    val mensajeConexion: String?
        get() {
            val cambios = when (pendientes) {
                0 -> null
                1 -> "1 cambio por enviar"
                else -> "$pendientes cambios por enviar"
            }
            return when {
                !conectado && cambios != null -> "Sin conexión · $cambios"
                !conectado -> "Sin conexión"
                else -> cambios
            }
        }

    /** «2 cambios no se pudieron enviar» o null. */
    val mensajeFallidas: String?
        get() = when (fallidas) {
            0 -> null
            1 -> "1 cambio no se pudo enviar"
            else -> "$fallidas cambios no se pudieron enviar"
        }
}

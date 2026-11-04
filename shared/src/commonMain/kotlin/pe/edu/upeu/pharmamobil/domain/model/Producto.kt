package pe.edu.upeu.pharmamobil.domain.model

data class Producto(
    val id:Long,
    val nombre:String,
    val precio:Double,
    val stock: Int,
    /** Motivo si PharmaSoft rechazo este producto creado sin red; null si no. */
    val rechazo: String? = null,
    /** Intentos de envío fallidos por un error transitorio (Guía 13). */
    val intentos: Int = 0,
    /** Último error transitorio al enviar; null si no hubo. */
    val ultimoError: String? = null
) {


    init {
        require(nombre.isNotBlank()) {
            "El nombre del producto no puede estar vacio"
        }
        require(precio > 0 && precio.isFinite()) {
            "El precio debe ser un numero mayor que cero"
        }
        require(stock >= 0) {
            "El stock no puede ser negativo"
        }
    }

    /**
     * Un producto necesita reposicion cuando su stock cae por debajo del
     * minimo que la botica mantiene en gondola.
     */
    val requiereReposicion: Boolean
        get() = stock < STOCK_MINIMO

    /** Pendiente que agotó sus intentos por fallos transitorios (Guía 13). */
    val agotoIntentos: Boolean
        get() = rechazo == null && intentos >= MAX_INTENTOS_ENVIO

    companion object {
        const val STOCK_MINIMO = 10

        /** Máximo de intentos de envío por fila antes de marcarla fallida. */
        const val MAX_INTENTOS_ENVIO = 5
    }
}

package pe.edu.upeu.pharmamobil.presentation.producto

data class ProductoUiState(
    val fase: Fase = Fase.Cargando,
    val formulario: FormularioProducto = FormularioProducto(),
    val registrando: Boolean = false,
    val mensajeExito: String? = null,
    /** Hay una sincronizacion con PharmaSoft en curso. */
    val sincronizando: Boolean = false,
    /** Aviso cuando la ultima sincronizacion fallo; la lista sigue visible. */
    val avisoRed: String? = null,
    /** Productos creados sin red que PharmaSoft todavia no conoce. */
    val pendientes: Int = 0,
    /** Productos creados sin red que PharmaSoft rechazo al sincronizar. */
    val rechazados: Int = 0,
    /** Instante (ms) de la ultima sincronizacion correcta; null si nunca hubo. */
    val ultimaSincronizacion: Long? = null,
    /** «Revisar»: la lista muestra solo las filas fallidas (Guía 13). */
    val soloFallidas: Boolean = false
) {

    /** Fases excluyentes del inventario: solo una puede estar activa. */
    sealed interface Fase {

        data object Cargando : Fase

        data object SinProductos : Fase

        data class ConProductos(val productos: List<ProductoUi>) : Fase

        data class Error(val mensaje: String) : Fase
    }
}

data class FormularioProducto(
    val nombre: String = "",
    val precio: String = "",
    val stock: String = "",
    val nombreError: String? = null,
    val precioError: String? = null,
    val stockError: String? = null
)

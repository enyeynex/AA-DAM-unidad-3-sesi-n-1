package pe.edu.upeu.pharmamobil.domain.permiso

/**
 * Gestor de permisos del sistema (Guía 13). Es una interfaz porque la
 * implementación de Android necesita la Activity para lanzar el diálogo y el
 * código común no puede nombrarla; cada plataforma la registra con Koin.
 */
enum class Permiso { CAMARA, UBICACION, NOTIFICACIONES }

sealed interface EstadoPermiso {
    data object Concedido : EstadoPermiso
    data object Denegado : EstadoPermiso               // se puede volver a pedir
    data object DenegadoParaSiempre : EstadoPermiso    // solo queda Ajustes
}

interface GestorPermisos {
    suspend fun estado(permiso: Permiso): EstadoPermiso
    suspend fun solicitar(permiso: Permiso): EstadoPermiso
    fun abrirAjustes()
}

/**
 * Tabla del paso 5. En Android, «procede explicar» es falso antes de la
 * primera solicitud y también tras una negación definitiva: para
 * distinguirlas hay que recordar si el permiso ya se pidió.
 */
fun decidirEstado(concedido: Boolean, yaSePidio: Boolean, procedeExplicar: Boolean): EstadoPermiso =
    when {
        concedido -> EstadoPermiso.Concedido
        !yaSePidio -> EstadoPermiso.Denegado
        procedeExplicar -> EstadoPermiso.Denegado
        else -> EstadoPermiso.DenegadoParaSiempre
    }

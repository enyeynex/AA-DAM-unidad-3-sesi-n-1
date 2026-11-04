package pe.edu.upeu.pharmamobil.domain.permiso

import pe.edu.upeu.pharmamobil.domain.port.Notificador

/**
 * Gestor de permisos simulado: la ventaja de haberlo declarado como interfaz
 * es que las pruebas no necesitan dispositivo ni diálogo del sistema.
 */
class FakeGestorPermisos(
    var actual: EstadoPermiso = EstadoPermiso.Denegado,
    /** Lo que «responde» el usuario en el diálogo. */
    var respuesta: EstadoPermiso = EstadoPermiso.Concedido
) : GestorPermisos {

    var solicitudes = 0
        private set
    var ajustesAbiertos = 0
        private set

    override suspend fun estado(permiso: Permiso): EstadoPermiso = actual

    override suspend fun solicitar(permiso: Permiso): EstadoPermiso {
        solicitudes++
        actual = respuesta
        return actual
    }

    override fun abrirAjustes() {
        ajustesAbiertos++
    }
}

class FakeNotificador : Notificador {
    val mostradas = mutableListOf<Pair<String, String>>()
    override fun mostrar(titulo: String, texto: String) {
        mostradas += titulo to texto
    }
}

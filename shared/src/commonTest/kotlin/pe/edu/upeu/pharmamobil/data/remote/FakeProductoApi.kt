package pe.edu.upeu.pharmamobil.data.remote

import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.model.Producto

/**
 * PharmaSoft simulado. Con [conRed] = false lanza el mismo error que
 * llamadaApi produce cuando no hay conexion. [eventos] registra el orden de
 * las llamadas para comprobar que se sube antes de bajar.
 */
class FakeProductoApi(
    servidor: List<Producto> = emptyList(),
    var conRed: Boolean = true
) : ProductoApi {

    val enServidor = servidor.toMutableList()
    val eventos = mutableListOf<String>()

    /** Error distinto de la falta de red (por ejemplo, un 401). */
    var fallo: FalloSesionException? = null

    /** Hace fallar solo la bajada, despues de que la subida funciono. */
    var fallaAlListar = false

    private var siguienteId = (servidor.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun listar(): List<Producto> {
        comprobar()
        if (fallaAlListar) throw FalloSesionException(ErrorSesion.SinConexion)
        eventos += "listar"
        return enServidor.toList()
    }

    override suspend fun registrar(producto: Producto): Producto {
        comprobar()
        eventos += "registrar ${producto.nombre}"
        val creado = producto.copy(id = siguienteId++)
        enServidor += creado
        return creado
    }

    private fun comprobar() {
        fallo?.let { throw it }
        if (!conRed) throw FalloSesionException(ErrorSesion.SinConexion)
    }
}

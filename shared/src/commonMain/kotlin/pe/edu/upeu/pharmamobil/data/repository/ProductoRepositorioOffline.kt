package pe.edu.upeu.pharmamobil.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.edu.upeu.pharmamobil.data.local.ProductoLocal
import pe.edu.upeu.pharmamobil.data.remote.ErrorServidorException
import pe.edu.upeu.pharmamobil.data.remote.ProductoApi
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository
import pe.edu.upeu.pharmamobil.platform.milisegundosActuales

/**
 * Repositorio offline-first: la base local es la unica fuente de verdad y la
 * red solo la mantiene al dia.
 */
class ProductoRepositorioOffline(
    private val local: ProductoLocal,
    private val remoto: ProductoApi,
    /** Reloj inyectable: las pruebas fijan el instante sin esperar. */
    private val ahora: () -> Long = ::milisegundosActuales
) : ProductoRepository {

    /**
     * El init del ViewModel y el registro pueden sincronizar a la vez: sin
     * este candado, un mismo pendiente se enviaria dos veces.
     */
    private val sincronizando = Mutex()

    override fun observar(): Flow<List<Producto>> = local.observar()

    /** Pendientes que la ultima subida envio, por su id local. */
    private val enviados = mutableMapOf<Long, Producto>()

    /**
     * Devuelve el producto como quedo: con el id del servidor si se envio,
     * con id local si sigue pendiente, o con el motivo si PharmaSoft lo rechazo.
     */
    override suspend fun registrar(producto: Producto): Producto {
        val guardado = local.guardarPendiente(producto)
        intentar { sincronizar() }
        return enviados.remove(guardado.id) ?: local.buscar(guardado.id) ?: guardado
    }

    override suspend fun descartar(id: Long) {
        local.descartarPendiente(id)
    }

    override fun ultimaSincronizacion(): Flow<Long?> = local.ultimaSincronizacion()

    /**
     * Primero subir, despues bajar. Al reves se pierde lo creado sin red.
     * El instante solo se guarda si las dos partes terminaron bien.
     */
    override suspend fun sincronizar() = sincronizando.withLock {
        subirPendientes()
        local.reemplazarSincronizados(remoto.listar())
        local.registrarSincronizacion(ahora())
    }

    /**
     * Un pendiente que PharmaSoft rechaza (por ejemplo, 409 por nombre
     * repetido) se marca y se sigue con los demas: si se lanzara el error,
     * ese producto bloquearia para siempre la subida y la bajada. La falta de
     * red y los errores de sesion (401, 403) si cortan la sincronizacion.
     */
    private suspend fun subirPendientes() {
        enviados.clear()
        local.pendientes().forEach { pendiente ->
            try {
                val enviado = remoto.registrar(pendiente)
                local.confirmarEnviado(pendiente.id, enviado)
                enviados[pendiente.id] = enviado
            } catch (rechazo: ErrorServidorException) {
                local.marcarRechazado(pendiente.id, rechazo.message ?: "Rechazado por PharmaSoft")
            }
        }
    }

    /** Un fallo de red no debe tumbar la operacion local. */
    private suspend fun intentar(bloque: suspend () -> Unit) {
        try {
            bloque()
        } catch (sinRed: FalloSesionException) {
            // Sin conexion queda pendiente y se reintentara al sincronizar.
            // Cualquier otro error (401, 403, validacion) si se informa.
            if (sinRed.error != ErrorSesion.SinConexion) throw sinRed
        }
    }
}

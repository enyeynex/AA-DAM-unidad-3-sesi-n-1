package pe.edu.upeu.pharmamobil.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.edu.upeu.pharmamobil.data.local.ProductoLocal
import pe.edu.upeu.pharmamobil.data.remote.ProductoApi
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository

/**
 * Repositorio offline-first: la base local es la unica fuente de verdad y la
 * red solo la mantiene al dia.
 */
class ProductoRepositorioOffline(
    private val local: ProductoLocal,
    private val remoto: ProductoApi
) : ProductoRepository {

    /**
     * El init del ViewModel y el registro pueden sincronizar a la vez: sin
     * este candado, un mismo pendiente se enviaria dos veces.
     */
    private val sincronizando = Mutex()

    override fun observar(): Flow<List<Producto>> = local.observar()

    override suspend fun registrar(producto: Producto): Producto {
        val guardado = local.guardarPendiente(producto)
        intentar { sincronizar() }
        return guardado
    }

    /** Primero subir, despues bajar. Al reves se pierde lo creado sin red. */
    override suspend fun sincronizar() = sincronizando.withLock {
        subirPendientes()
        local.reemplazarSincronizados(remoto.listar())
    }

    private suspend fun subirPendientes() {
        local.pendientes().forEach { pendiente ->
            val enviado = remoto.registrar(pendiente)
            local.confirmarEnviado(pendiente.id, enviado)
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

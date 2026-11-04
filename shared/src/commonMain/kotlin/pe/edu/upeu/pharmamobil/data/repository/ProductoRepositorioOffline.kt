package pe.edu.upeu.pharmamobil.data.repository

import kotlinx.coroutines.flow.Flow
import pe.edu.upeu.pharmamobil.data.local.ProductoLocal
import pe.edu.upeu.pharmamobil.data.remote.ProductoApi
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.model.ResumenSync
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository
import pe.edu.upeu.pharmamobil.domain.usecase.SincronizarUseCase
import pe.edu.upeu.pharmamobil.platform.milisegundosActuales

/**
 * Repositorio offline-first: la base local es la unica fuente de verdad y la
 * red solo la mantiene al dia. Desde la Guía 13 el algoritmo de subida y
 * bajada vive en [SincronizarUseCase]; este repositorio guarda en local y
 * lo invoca.
 */
class ProductoRepositorioOffline(
    private val local: ProductoLocal,
    private val sincronizarCaso: SincronizarUseCase
) : ProductoRepository {

    /** Usado por las pruebas: arma el caso de uso con la API real o simulada. */
    constructor(
        local: ProductoLocal,
        remoto: ProductoApi,
        ahora: () -> Long = ::milisegundosActuales
    ) : this(local, SincronizarUseCase(local, EnvioRepositorioRest(remoto), ahora))

    override fun observar(): Flow<List<Producto>> = local.observar()

    /**
     * Devuelve el producto como quedo: con el id del servidor si se envio,
     * con id local si sigue pendiente, o con el motivo si PharmaSoft lo rechazo.
     */
    override suspend fun registrar(producto: Producto): Producto {
        val guardado = local.guardarPendiente(producto)
        var confirmado: Producto? = null
        intentar { confirmado = sincronizar().confirmados[guardado.id] }
        return confirmado ?: local.buscar(guardado.id) ?: guardado
    }

    override suspend fun descartar(id: Long) {
        local.descartarPendiente(id)
    }

    override fun ultimaSincronizacion(): Flow<Long?> = local.ultimaSincronizacion()

    override suspend fun sincronizar(): ResumenSync = sincronizarCaso()

    override suspend fun reintentarFallidas(): ResumenSync = sincronizarCaso.reintentarFallidas()

    override fun observarPendientes(): Flow<Int> = local.observarPendientes(SincronizarUseCase.MAX_INTENTOS)

    override fun observarFallidas(): Flow<Int> = local.observarFallidas(SincronizarUseCase.MAX_INTENTOS)

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

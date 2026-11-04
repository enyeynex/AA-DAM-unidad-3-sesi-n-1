package pe.edu.upeu.pharmamobil.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pe.edu.upeu.pharmamobil.db.PharmaDb
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.repository.ProductoLocalRepository
import pe.edu.upeu.pharmamobil.domain.usecase.SincronizarUseCase
import pe.edu.upeu.pharmamobil.db.Producto as FilaProducto

/**
 * Capa local del inventario: concentra las consultas de Producto.sq y
 * traduce entre las filas generadas por SQLDelight y el modelo de dominio.
 * No conoce Ktor ni los DTO de PharmaSoft.
 */
class ProductoLocal(base: PharmaDb) : ProductoLocalRepository {

    private val consultas = base.productoQueries
    private val sincronizaciones = base.sincronizacionQueries

    /** Emite de nuevo cada vez que la tabla producto cambia. */
    fun observar(): Flow<List<Producto>> =
        consultas.listarTodos()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { filas -> filas.map { it.aDominio() } }

    /** Guarda lo creado sin red con un id negativo y la marca pendiente = 1. */
    fun guardarPendiente(producto: Producto): Producto {
        val menor = consultas.siguienteIdLocal().executeAsOne()
        val local = producto.copy(id = minOf(menor, 0L) - 1L)
        consultas.insertar(
            local.id, local.nombre, local.precio,
            local.stock.toLong(), 1L
        )
        return local
    }

    /**
     * PharmaSoft acepto el pendiente: la fila con id local se cambia por la
     * del servidor, con su id real. Si la bajada posterior falla, el producto
     * ya no se vuelve a enviar y no se duplica en el servidor.
     */
    override fun confirmar(idLocal: Long, delServidor: Producto) {
        consultas.transaction {
            consultas.borrarPorId(idLocal)
            consultas.insertar(
                delServidor.id, delServidor.nombre, delServidor.precio,
                delServidor.stock.toLong(), 0L
            )
        }
    }

    /**
     * Recibe la lista del servidor. Solo borra las filas con pendiente = 0:
     * lo que el usuario creo sin red no se toca. Todo va en una transaccion
     * para que el Flow emita una sola vez y la lista no parpadee.
     */
    override fun conciliar(delServidor: List<Producto>) = reemplazarSincronizados(delServidor)

    fun reemplazarSincronizados(productos: List<Producto>) {
        consultas.transaction {
            consultas.borrarSincronizados()
            productos.forEach {
                consultas.insertar(
                    it.id, it.nombre, it.precio,
                    it.stock.toLong(), 0L
                )
            }
        }
    }

    fun buscar(id: Long): Producto? =
        consultas.buscarPorId(id).executeAsOneOrNull()?.aDominio()

    /** PharmaSoft no acepto el pendiente: se guarda el motivo y deja de enviarse. */
    override fun marcarFallida(id: Long, motivo: String) {
        consultas.marcarRechazado(motivo, id)
    }

    /** Solo borra filas que el servidor no conoce (pendiente = 1). */
    fun descartarPendiente(id: Long) {
        consultas.descartarPendiente(id)
    }

    /** Pendientes que todavía pueden enviarse: sin rechazo y con intentos disponibles, en orden de creación. */
    override fun pendientes(maxIntentos: Int): List<Producto> =
        consultas.pendientesPorEnviar(maxIntentos.toLong()).executeAsList()
            .map { it.aDominio() }

    /** Atajo para pruebas y diagnóstico: con el máximo de intentos del caso de uso. */
    fun pendientes(): List<Producto> = pendientes(SincronizarUseCase.MAX_INTENTOS)

    override fun observarPendientes(maxIntentos: Int): Flow<Int> =
        consultas.contarPendientes(maxIntentos.toLong())
            .asFlow()
            .mapToOne(Dispatchers.Default)
            .map { it.toInt() }

    override fun observarFallidas(maxIntentos: Int): Flow<Int> =
        consultas.contarFallidas(maxIntentos.toLong())
            .asFlow()
            .mapToOne(Dispatchers.Default)
            .map { it.toInt() }

    override fun contarPendientes(maxIntentos: Int): Int =
        consultas.contarPendientes(maxIntentos.toLong()).executeAsOne().toInt()

    /** Fallo transitorio: suma un intento y recuerda el motivo. */
    override fun sumarIntento(id: Long, motivo: String) {
        consultas.sumarIntento(motivo, id)
    }

    /** «Reintentar ahora»: devuelve a la cola las filas que agotaron sus intentos. */
    override fun reiniciarIntentos(maxIntentos: Int) {
        consultas.reiniciarIntentos(maxIntentos.toLong())
    }

    /** Instante (ms) de la ultima sincronizacion correcta; null si nunca hubo. */
    fun ultimaSincronizacion(): Flow<Long?> =
        sincronizaciones.ultima()
            .asFlow()
            .mapToOneOrNull(Dispatchers.Default)

    override fun registrarSincronizacion(instante: Long) {
        sincronizaciones.registrar(instante)
    }

    private fun FilaProducto.aDominio() = Producto(
        id = id,
        nombre = nombre,
        precio = precio,
        stock = stock.toInt(),
        rechazo = rechazo,
        intentos = intentos.toInt(),
        ultimoError = ultimoError
    )
}

package pe.edu.upeu.pharmamobil.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository

/**
 * Doble del inventario para las pruebas del ViewModel y los casos de uso.
 * Un StateFlow hace de tabla local: registrar escribe en el y la suscripcion
 * emite, igual que con SQLDelight. Puede fallar a voluntad.
 */
class FakeProductoRepository(
    productos: List<Producto> = emptyList()
) : ProductoRepository {

    val tabla = MutableStateFlow(productos)

    var fallaAlRegistrar: Throwable? = null
    var fallaAlObservar: Throwable? = null
    var fallaAlSincronizar: Throwable? = null

    /** Lo que «PharmaSoft» devuelve al sincronizar; null deja la tabla igual. */
    var delServidor: List<Producto>? = null

    var sincronizaciones = 0
        private set

    private var siguienteId = 1L

    override fun observar(): Flow<List<Producto>> =
        fallaAlObservar?.let { fallo -> flow { throw fallo } } ?: tabla

    override suspend fun registrar(producto: Producto): Producto {

        fallaAlRegistrar?.let { throw it }

        val guardado = producto.copy(id = siguienteId++)
        tabla.update { it + guardado }
        return guardado
    }

    override suspend fun sincronizar() {
        sincronizaciones++
        fallaAlSincronizar?.let { throw it }
        delServidor?.let { tabla.value = it }
    }
}

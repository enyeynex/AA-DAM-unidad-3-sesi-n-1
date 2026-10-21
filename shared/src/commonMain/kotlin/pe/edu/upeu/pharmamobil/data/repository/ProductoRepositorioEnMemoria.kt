package pe.edu.upeu.pharmamobil.data.repository

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository

/**
 * Almacenamiento en memoria de productos (sesiones 5 a 9). Ya no lo registra
 * Koin, pero se conserva adaptado al contrato offline-first: un StateFlow
 * hace las veces de la tabla observable.
 *
 * El [Mutex] protege el contador de ids de dos registros que se crucen.
 */
class ProductoRepositorioEnMemoria : ProductoRepository {

    private val candado = Mutex()
    private val productos = MutableStateFlow<List<Producto>>(emptyList())
    private var siguienteId = 1L

    override fun observar(): Flow<List<Producto>> = productos.asStateFlow()

    override suspend fun registrar(producto: Producto): Producto {
        delay(RETARDO_REGISTRO_MS)
        return candado.withLock {
            val guardado = producto.copy(id = siguienteId++)
            productos.update { it + guardado }
            guardado
        }
    }

    /** No hay servidor: no hay nada que sincronizar. */
    override suspend fun sincronizar() = Unit

    private companion object {
        const val RETARDO_REGISTRO_MS = 400L
    }
}

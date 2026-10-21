package pe.edu.upeu.pharmamobil.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.edu.upeu.pharmamobil.domain.model.Producto

/**
 * Contrato offline-first del inventario (sesion 11): la pantalla ya no pide
 * una lista, se suscribe a la base local.
 */
interface ProductoRepository {

    /** La pantalla se suscribe una vez y se actualiza sola. */
    fun observar(): Flow<List<Producto>>

    /** Guarda en local; intenta enviar si hay conexion. */
    suspend fun registrar(producto: Producto): Producto

    /** Sube lo pendiente y luego trae la lista del servidor. */
    suspend fun sincronizar()

    /** Instante (ms) de la ultima sincronizacion correcta; null si nunca hubo. */
    fun ultimaSincronizacion(): Flow<Long?>
}

package pe.edu.upeu.pharmamobil.domain.repository

import kotlinx.coroutines.flow.Flow
import pe.edu.upeu.pharmamobil.domain.model.Producto

/** Lo que la sincronización necesita de la base local (Guía 13). */
interface ProductoLocalRepository {
    fun pendientes(maxIntentos: Int): List<Producto>
    fun confirmar(idLocal: Long, delServidor: Producto)
    fun marcarFallida(id: Long, motivo: String)
    fun sumarIntento(id: Long, motivo: String)
    fun conciliar(delServidor: List<Producto>)
    fun contarPendientes(maxIntentos: Int): Int
    fun reiniciarIntentos(maxIntentos: Int)
    fun registrarSincronizacion(instante: Long)
    fun observarPendientes(maxIntentos: Int): Flow<Int>
    fun observarFallidas(maxIntentos: Int): Flow<Int>
}

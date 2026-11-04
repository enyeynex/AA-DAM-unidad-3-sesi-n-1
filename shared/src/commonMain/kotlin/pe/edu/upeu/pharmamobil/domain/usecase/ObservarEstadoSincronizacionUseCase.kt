package pe.edu.upeu.pharmamobil.domain.usecase

import kotlinx.coroutines.flow.Flow
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository

/** Contadores observables de la base local para el estado de sincronización. */
class ObservarEstadoSincronizacionUseCase(
    private val productoRepository: ProductoRepository
) {
    fun pendientes(): Flow<Int> = productoRepository.observarPendientes()
    fun fallidas(): Flow<Int> = productoRepository.observarFallidas()
}

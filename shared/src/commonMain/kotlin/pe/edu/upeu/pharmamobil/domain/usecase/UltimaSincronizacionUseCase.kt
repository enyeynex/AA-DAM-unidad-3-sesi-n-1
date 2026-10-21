package pe.edu.upeu.pharmamobil.domain.usecase

import kotlinx.coroutines.flow.Flow
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository

/** Cuando se sincronizo bien por ultima vez (ms), o null si nunca. */
class UltimaSincronizacionUseCase(
    private val productoRepository: ProductoRepository
) {

    operator fun invoke(): Flow<Long?> = productoRepository.ultimaSincronizacion()
}

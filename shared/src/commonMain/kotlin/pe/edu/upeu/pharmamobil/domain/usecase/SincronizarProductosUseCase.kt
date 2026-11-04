package pe.edu.upeu.pharmamobil.domain.usecase

import pe.edu.upeu.pharmamobil.domain.model.ResumenSync
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository

/** Sube lo pendiente y trae el inventario del servidor a la base local. */
class SincronizarProductosUseCase(
    private val productoRepository: ProductoRepository
) {

    suspend operator fun invoke(): Result<ResumenSync> = resultadoDe {
        productoRepository.sincronizar()
    }

    /** «Reintentar ahora» sobre las filas fallidas. */
    suspend fun reintentarFallidas(): Result<ResumenSync> = resultadoDe {
        productoRepository.reintentarFallidas()
    }
}

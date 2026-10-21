package pe.edu.upeu.pharmamobil.domain.usecase

import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository

/** Sube lo pendiente y trae el inventario del servidor a la base local. */
class SincronizarProductosUseCase(
    private val productoRepository: ProductoRepository
) {

    suspend operator fun invoke(): Result<Unit> = resultadoDe {
        productoRepository.sincronizar()
    }
}

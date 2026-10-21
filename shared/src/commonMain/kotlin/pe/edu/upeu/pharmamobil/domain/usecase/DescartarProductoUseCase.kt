package pe.edu.upeu.pharmamobil.domain.usecase

import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository

/** Quita de la base local un producto que PharmaSoft rechazo o aun no conoce. */
class DescartarProductoUseCase(
    private val productoRepository: ProductoRepository
) {

    suspend operator fun invoke(id: Long): Result<Unit> = resultadoDe {
        productoRepository.descartar(id)
    }
}

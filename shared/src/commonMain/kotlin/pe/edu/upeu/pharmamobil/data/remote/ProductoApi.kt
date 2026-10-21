package pe.edu.upeu.pharmamobil.data.remote

import pe.edu.upeu.pharmamobil.domain.model.Producto

/**
 * Lo que el repositorio offline necesita de PharmaSoft. Es un contrato de la
 * capa de datos: la pantalla nunca habla con la red directamente.
 */
interface ProductoApi {
    suspend fun listar(): List<Producto>
    suspend fun registrar(producto: Producto): Producto
}

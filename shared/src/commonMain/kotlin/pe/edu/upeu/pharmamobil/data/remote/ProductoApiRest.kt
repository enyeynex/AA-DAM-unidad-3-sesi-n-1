package pe.edu.upeu.pharmamobil.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import pe.edu.upeu.pharmamobil.data.remote.dto.CategoriaDto
import pe.edu.upeu.pharmamobil.data.remote.dto.CategoriaRequestDto
import pe.edu.upeu.pharmamobil.data.remote.dto.PaginaDto
import pe.edu.upeu.pharmamobil.data.remote.dto.ProductoDto
import pe.edu.upeu.pharmamobil.data.remote.dto.ProductoRequestDto
import pe.edu.upeu.pharmamobil.data.remote.dto.aDominio
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.sesion.AvisosDeSesion

/**
 * Inventario en PharmaSoft. El token no aparece aqui: lo adjunta el plugin
 * Auth del HttpClient en cada peticion. Desde la sesion 11 ya no es el
 * repositorio de la pantalla: lo usa [pe.edu.upeu.pharmamobil.data.repository.ProductoRepositorioOffline]
 * para sincronizar la base local.
 */
class ProductoApiRest(
    private val cliente: HttpClient,
    private val avisos: AvisosDeSesion
) : ProductoApi {

    override suspend fun listar(): List<Producto> = llamadaApi(avisos) {
        cliente.get("productos") {
            parameter("pagina", 0)
            parameter("tamanio", TAMANIO_PAGINA)
        }.body<PaginaDto<ProductoDto>>().contenido.map { it.aDominio() }
    }

    override suspend fun registrar(producto: Producto): Producto = llamadaApi(avisos) {
        val categoriaId = categoriaPorDefecto()
        cliente.post("productos") {
            setBody(
                ProductoRequestDto(
                    nombre = producto.nombre,
                    precio = producto.precio,
                    stock = producto.stock,
                    estado = true,
                    categoriaId = categoriaId
                )
            )
        }.body<ProductoDto>().aDominio()
    }

    /**
     * PharmaSoft exige una categoria y el formulario de la app todavia no la
     * pide: se usa la primera que exista o se crea «General» si no hay
     * ninguna (la base de datos arranca vacia).
     */
    private suspend fun categoriaPorDefecto(): Long {
        val existentes: List<CategoriaDto> = cliente.get("categorias").body()
        existentes.firstOrNull()?.let { return it.id }

        return cliente.post("categorias") {
            setBody(CategoriaRequestDto("General", "Creada desde PharmaMobil", true))
        }.body<CategoriaDto>().id
    }

    private companion object {
        const val TAMANIO_PAGINA = 100
    }
}

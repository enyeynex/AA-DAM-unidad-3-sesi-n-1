package pe.edu.upeu.pharmamobil.domain.repository

import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.model.ResultadoEnvio

/** Lo que la sincronización necesita de PharmaSoft, sin hablar de HTTP. */
interface ProductoRemotoRepository {

    /** Nunca lanza por un error del servicio: lo traduce a [ResultadoEnvio]. */
    suspend fun enviar(producto: Producto): ResultadoEnvio

    /** Inventario del servidor; lanza si no hay conexión. */
    suspend fun listar(): List<Producto>
}

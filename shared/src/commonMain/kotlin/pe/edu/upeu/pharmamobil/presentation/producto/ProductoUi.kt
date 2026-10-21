package pe.edu.upeu.pharmamobil.presentation.producto

import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.platform.formatearSoles


data class ProductoUi(
    val id: Long,
    val nombre: String,
    val precio: String,
    val stock: String,
    val requiereReposicion: Boolean,
    /** Id negativo: el producto se creo sin red y aun no existe en PharmaSoft. */
    val pendiente: Boolean = false,
    /** Motivo si PharmaSoft rechazo el producto; se muestra con «Descartar». */
    val rechazo: String? = null
)

/**
 * El formato de moneda se aplica aqui, en el mapeo de presentacion: ni el
 * modelo de dominio ni el composable saben como se escribe un precio.
 * formatearSoles es un expect, asi que cada plataforma usa su formateador.
 */
fun Producto.aUi(): ProductoUi = ProductoUi(
    id = id,
    nombre = nombre,
    precio = formatearSoles(precio),
    stock = "$stock u.",
    requiereReposicion = requiereReposicion,
    pendiente = id < 0 && rechazo == null,
    rechazo = rechazo
)

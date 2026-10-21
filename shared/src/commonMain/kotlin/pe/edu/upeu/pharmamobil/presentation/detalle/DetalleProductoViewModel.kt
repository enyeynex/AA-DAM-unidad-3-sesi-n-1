package pe.edu.upeu.pharmamobil.presentation.detalle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.platform.Compartidor
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository
import pe.edu.upeu.pharmamobil.domain.usecase.comoTextoParaCompartir
import pe.edu.upeu.pharmamobil.domain.usecase.resultadoDe
import pe.edu.upeu.pharmamobil.presentation.producto.aUi

/**
 * ViewModel del detalle de un producto. Recibe el [Compartidor] por su
 * interfaz de dominio: no sabe si detras hay un Intent de Android o un
 * UIActivityViewController de iOS, eso lo decide el platformModule.
 */
class DetalleProductoViewModel(
    private val repository: ProductoRepository,
    private val compartidor: Compartidor
) : ViewModel() {

    private val _uiState = MutableStateFlow<DetalleProductoUiState>(DetalleProductoUiState.Cargando)
    val uiState: StateFlow<DetalleProductoUiState> = _uiState.asStateFlow()

    /** El producto de dominio se queda aqui; la pantalla solo ve ProductoUi. */
    private var productoActual: Producto? = null

    private var carga: Job? = null

    fun cargar(id: Long) {

        carga?.cancel()

        carga = viewModelScope.launch {

            productoActual = null
            _uiState.value = DetalleProductoUiState.Cargando

            // El producto se toma de la base local: el detalle tambien
            // funciona sin conexion.
            _uiState.value = resultadoDe {
                repository.observar().first().firstOrNull { it.id == id }
            }.fold(
                onSuccess = { producto ->
                    if (producto == null) {
                        DetalleProductoUiState.Error("El producto ya no está en el inventario")
                    } else {
                        productoActual = producto
                        DetalleProductoUiState.Listo(producto.aUi())
                    }
                },
                onFailure = { fallo ->
                    DetalleProductoUiState.Error(
                        fallo.message ?: "No se pudo cargar el producto"
                    )
                }
            )
        }
    }

    fun compartir(producto: Producto) {
        compartidor.compartir(producto.comoTextoParaCompartir())
    }

    /** Accion del boton «Compartir»: comparte el producto que esta en pantalla. */
    fun compartirProductoActual() {
        productoActual?.let { compartir(it) }
    }

    /** Al salir del detalle se descarta el producto para no mostrarlo al abrir otro. */
    fun limpiar() {
        carga?.cancel()
        productoActual = null
        _uiState.value = DetalleProductoUiState.Cargando
    }
}

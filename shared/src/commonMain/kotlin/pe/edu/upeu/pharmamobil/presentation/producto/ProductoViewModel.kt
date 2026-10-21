package pe.edu.upeu.pharmamobil.presentation.producto

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.usecase.ObservarProductosUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.ProductoInvalidoException
import pe.edu.upeu.pharmamobil.domain.usecase.RegistrarProductoUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.SincronizarProductosUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.UltimaSincronizacionUseCase

/**
 * Offline-first (sesion 11): el ViewModel no pide la lista. Se suscribe una
 * vez a la base local y reacciona a lo que emita; la red solo actualiza esa
 * base mediante [sincronizar].
 */
class ProductoViewModel(
    private val registrarProducto: RegistrarProductoUseCase,
    private val observarProductos: ObservarProductosUseCase,
    private val sincronizarProductos: SincronizarProductosUseCase,
    private val ultimaSincronizacion: UltimaSincronizacionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductoUiState())
    val uiState: StateFlow<ProductoUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observarProductos()
                .catch { fallo ->
                    _uiState.update {
                        it.copy(
                            fase = ProductoUiState.Fase.Error(
                                fallo.message ?: "No se pudo leer el inventario guardado"
                            )
                        )
                    }
                }
                .collect { productos ->
                    _uiState.update {
                        it.copy(
                            fase = faseDe(productos),
                            pendientes = productos.count { p -> p.id < 0 }
                        )
                    }
                }
        }
        viewModelScope.launch {
            ultimaSincronizacion()
                .catch { /* sin el dato se muestra «nunca sincronizado» */ }
                .collect { instante ->
                    _uiState.update { it.copy(ultimaSincronizacion = instante) }
                }
        }
        sincronizar()
    }

    /** Accion del init y del boton «Sincronizar». No devuelve datos: el Flow emite. */
    fun sincronizar() {

        if (_uiState.value.sincronizando) return

        viewModelScope.launch {
            _uiState.update { it.copy(sincronizando = true) }
            val resultado = sincronizarProductos()
            _uiState.update {
                it.copy(
                    sincronizando = false,
                    avisoRed = resultado.exceptionOrNull()?.let { fallo -> avisoDe(fallo) }
                )
            }
        }
    }

    fun onNombreChange(nombre: String) {
        _uiState.update {
            it.copy(
                formulario = it.formulario.copy(nombre = nombre, nombreError = null),
                mensajeExito = null
            )
        }
    }

    fun onPrecioChange(precio: String) {
        _uiState.update {
            it.copy(
                formulario = it.formulario.copy(precio = precio, precioError = null),
                mensajeExito = null
            )
        }
    }

    fun onStockChange(stock: String) {
        _uiState.update {
            it.copy(
                formulario = it.formulario.copy(stock = stock, stockError = null),
                mensajeExito = null
            )
        }
    }

    fun registrar() {

        if (_uiState.value.registrando) return

        viewModelScope.launch {

            _uiState.update {
                it.copy(registrando = true, mensajeExito = null)
            }

            val formulario = _uiState.value.formulario

            registrarProducto(
                nombre = formulario.nombre,
                precio = formulario.precio,
                stock = formulario.stock
            ).fold(
                // Sin recargar nada: el producto ya esta en la tabla y el Flow emitio.
                onSuccess = { producto ->
                    _uiState.update {
                        it.copy(
                            registrando = false,
                            formulario = FormularioProducto(),
                            mensajeExito = if (producto.id < 0) {
                                "Producto \"${producto.nombre}\" guardado sin conexión: se enviará al sincronizar"
                            } else {
                                "Producto \"${producto.nombre}\" registrado correctamente"
                            }
                        )
                    }
                },
                onFailure = { fallo ->
                    when (fallo) {

                        is ProductoInvalidoException -> _uiState.update {
                            it.copy(
                                registrando = false,
                                formulario = it.formulario.copy(
                                    nombreError = fallo.errores.nombre,
                                    precioError = fallo.errores.precio,
                                    stockError = fallo.errores.stock
                                )
                            )
                        }

                        // El producto quedo guardado en local; solo fallo el envio.
                        else -> _uiState.update {
                            it.copy(
                                registrando = false,
                                avisoRed = avisoDe(fallo)
                            )
                        }
                    }
                }
            )
        }
    }

    private fun faseDe(productos: List<Producto>): ProductoUiState.Fase =
        if (productos.isEmpty()) {
            ProductoUiState.Fase.SinProductos
        } else {
            ProductoUiState.Fase.ConProductos(productos.map { it.aUi() })
        }

    private fun avisoDe(fallo: Throwable): String =
        if (fallo is FalloSesionException && fallo.error == ErrorSesion.SinConexion) {
            AVISO_SIN_CONEXION
        } else {
            "No se pudo sincronizar: ${fallo.message ?: "error desconocido"}"
        }

    companion object {
        const val AVISO_SIN_CONEXION = "Sin conexión: mostrando datos guardados"
    }
}

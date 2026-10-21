package pe.edu.upeu.pharmamobil.presentation.diagnostico

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upeu.pharmamobil.domain.sesion.DiagnosticoSesion
import pe.edu.upeu.pharmamobil.domain.sesion.EstadoToken
import pe.edu.upeu.pharmamobil.domain.sesion.ResultadoDiagnostico
import kotlinx.coroutines.flow.first
import pe.edu.upeu.pharmamobil.domain.usecase.ObservarProductosUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.SincronizarProductosUseCase

data class DiagnosticoUiState(
    val estadoToken: EstadoToken = EstadoToken(guardado = false),
    val ejecutando: Boolean = false,
    val ultimaPrueba: String? = null,
    val resultado: String? = null
)

/**
 * Pruebas de la actividad autonoma de la sesion 10. «Consultar inventario»
 * usa el caso de uso real (desde la sesion 11, la sincronizacion): si el
 * servidor responde 401, la app cierra la sesion igual que en cualquier
 * otra pantalla.
 */
class DiagnosticoViewModel(
    private val diagnostico: DiagnosticoSesion,
    private val sincronizarProductos: SincronizarProductosUseCase,
    private val observarProductos: ObservarProductosUseCase,
    val esDepuracion: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticoUiState())
    val uiState: StateFlow<DiagnosticoUiState> = _uiState.asStateFlow()

    init {
        refrescarEstado()
    }

    fun refrescarEstado() {
        _uiState.update { it.copy(estadoToken = diagnostico.estadoToken()) }
    }

    fun consultarInventario() = ejecutar("Consultar inventario") {
        sincronizarProductos().fold(
            onSuccess = { "HTTP 200 · ${observarProductos().first().size} productos" },
            onFailure = { it.message ?: "Error" }
        )
    }

    fun alterarToken() = ejecutar("Alterar token guardado") {
        if (diagnostico.alterarTokenGuardado()) {
            "Se cambió un carácter de la firma del token guardado. Ahora pulsa «Consultar inventario»."
        } else {
            "No hay token guardado"
        }
    }

    fun prefijoEnMinuscula() = ejecutar("Prefijo «bearer» en minúscula") {
        describir(diagnostico.peticionConPrefijoEnMinuscula())
    }

    fun peticionSinSesion() = ejecutar("Petición después de cerrar sesión") {
        describir(diagnostico.peticionConElClienteActual())
    }

    private fun ejecutar(nombre: String, prueba: suspend () -> String) {
        if (_uiState.value.ejecutando) return
        viewModelScope.launch {
            _uiState.update { it.copy(ejecutando = true, ultimaPrueba = nombre, resultado = null) }
            val texto = prueba()
            _uiState.update {
                it.copy(ejecutando = false, resultado = texto, estadoToken = diagnostico.estadoToken())
            }
        }
    }

    private fun describir(resultado: ResultadoDiagnostico): String {
        val codigo = resultado.codigo?.let { "HTTP $it" } ?: "Sin respuesta"
        val cabecera = when (resultado.conCabeceraAuthorization) {
            true -> " · Cabecera Authorization: enviada"
            false -> " · Cabecera Authorization: no enviada"
            null -> ""
        }
        return "$codigo · «${resultado.mensaje}»$cabecera"
    }
}

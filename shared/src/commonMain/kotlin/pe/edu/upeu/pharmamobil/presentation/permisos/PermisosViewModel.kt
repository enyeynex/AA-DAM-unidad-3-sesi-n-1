package pe.edu.upeu.pharmamobil.presentation.permisos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.edu.upeu.pharmamobil.domain.permiso.EstadoPermiso
import pe.edu.upeu.pharmamobil.domain.permiso.GestorPermisos
import pe.edu.upeu.pharmamobil.domain.permiso.Permiso
import pe.edu.upeu.pharmamobil.domain.port.Notificador
import pe.edu.upeu.pharmamobil.domain.port.ServicioPush
import pe.edu.upeu.pharmamobil.domain.usecase.ObservarProductosUseCase

data class PermisosUiState(
    val estado: EstadoPermiso? = null,
    val solicitando: Boolean = false,
    val mensaje: String? = null,
    /** Inicio del token de Firebase Messaging, como evidencia de la integración. */
    val tokenPush: String? = null
)

/**
 * Notificaciones de stock bajo (Guía 13, paso 5): explica antes de pedir,
 * distingue los tres estados y ofrece Ajustes cuando ya no se puede pedir.
 */
class PermisosViewModel(
    private val gestor: GestorPermisos,
    private val notificador: Notificador,
    private val observarProductos: ObservarProductosUseCase,
    private val servicioPush: ServicioPush? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermisosUiState())
    val uiState: StateFlow<PermisosUiState> = _uiState.asStateFlow()

    init {
        refrescar()
    }

    /** Al volver de Ajustes el usuario pudo cambiar el permiso: se consulta de nuevo. */
    fun refrescar() {
        viewModelScope.launch {
            val estado = gestor.estado(Permiso.NOTIFICACIONES)
            _uiState.update { it.copy(estado = estado) }
            if (estado == EstadoPermiso.Concedido && _uiState.value.tokenPush == null) {
                val token = servicioPush?.token()
                _uiState.update { it.copy(tokenPush = token?.take(12)?.let { inicio -> "$inicio…" }) }
            }
        }
    }

    fun activar() {
        if (_uiState.value.solicitando) return
        viewModelScope.launch {
            _uiState.update { it.copy(solicitando = true, mensaje = null) }
            val estado = gestor.solicitar(Permiso.NOTIFICACIONES)
            _uiState.update { it.copy(solicitando = false, estado = estado) }
        }
    }

    fun abrirAjustes() = gestor.abrirAjustes()

    /** Uso real del permiso: avisa qué productos de la base local requieren reposición. */
    fun probarNotificacion() {
        viewModelScope.launch {
            val porReponer = observarProductos().first().filter { it.requiereReposicion }
            val texto = when (porReponer.size) {
                0 -> "Ningún producto necesita reposición por ahora."
                1 -> "${porReponer.first().nombre} necesita reposición."
                else -> "${porReponer.first().nombre} y ${porReponer.size - 1} productos más necesitan reposición."
            }
            notificador.mostrar("Stock bajo en PharmaMobil", texto)
            _uiState.update { it.copy(mensaje = "Notificación enviada") }
        }
    }
}

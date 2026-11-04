package pe.edu.upeu.pharmamobil.presentation.permisos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upeu.pharmamobil.domain.permiso.EstadoPermiso

/** Tarjeta «Notificaciones de stock bajo» con los tres estados del permiso. */
@Composable
fun TarjetaNotificaciones(viewModel: PermisosViewModel, modifier: Modifier = Modifier) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Al volver de Ajustes se vuelve a consultar el estado.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refrescar() }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val concedido = uiState.estado == EstadoPermiso.Concedido
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = if (concedido) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    contentDescription = null
                )
                Text("Notificaciones de stock bajo", style = MaterialTheme.typography.titleMedium)
            }

            when (uiState.estado) {
                EstadoPermiso.Concedido -> {
                    Text("Activadas. Te avisaremos cuando un producto necesite reposición.")
                    OutlinedButton(onClick = viewModel::probarNotificacion) { Text("Probar notificación") }
                    uiState.tokenPush?.let {
                        Text("Firebase Messaging · token $it", style = MaterialTheme.typography.labelSmall)
                    }
                }
                EstadoPermiso.Denegado -> {
                    // Se explica ANTES de pedir el permiso.
                    Text(
                        "Te avisaremos cuando un producto necesite reposición. " +
                            "Para eso PharmaMobil necesita permiso para mostrar notificaciones."
                    )
                    Button(onClick = viewModel::activar, enabled = !uiState.solicitando) {
                        Text("Activar")
                    }
                }
                EstadoPermiso.DenegadoParaSiempre -> {
                    Text("Las desactivaste. Actívalas desde Ajustes para recibir los avisos de stock bajo.")
                    Button(onClick = viewModel::abrirAjustes) { Text("Abrir Ajustes") }
                }
                null -> Text("Consultando el permiso…")
            }

            uiState.mensaje?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

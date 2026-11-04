package pe.edu.upeu.pharmamobil.presentation.producto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import pe.edu.upeu.pharmamobil.platform.milisegundosActuales
import pe.edu.upeu.pharmamobil.presentation.sincronizacion.SincronizacionUiState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.edu.upeu.pharmamobil.presentation.components.EstadoVacio
import pe.edu.upeu.pharmamobil.presentation.components.MensajeExito
import pe.edu.upeu.pharmamobil.presentation.components.ValidatedTextField

@Composable
fun ProductoScreen(
    viewModel: ProductoViewModel,
    modifier: Modifier = Modifier,
    puedeRegistrar: Boolean = true,
    onVerDetalle: (Long) -> Unit = {}
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // «Hace 5 minutos» envejece aunque no cambie nada: se recalcula cada minuto.
    val ahora by produceState(milisegundosActuales()) {
        while (true) {
            delay(60_000L)
            value = milisegundosActuales()
        }
    }
    val nuncaSincronizado = uiState.ultimaSincronizacion == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Solo quien tiene CATALOGO_ESCRIBIR ve el formulario: el servidor
        // responderia 403 a los demas.
        if (puedeRegistrar) {
            FormularioProductoCard(
                formulario = uiState.formulario,
                registrando = uiState.registrando,
                onNombreChange = viewModel::onNombreChange,
                onPrecioChange = viewModel::onPrecioChange,
                onStockChange = viewModel::onStockChange,
                onRegistrar = viewModel::registrar
            )
        }

        uiState.mensajeExito?.let {
            MensajeExito(it)
        }

        val estadoSync by viewModel.estadoSync.collectAsStateWithLifecycle()

        // Guía 13: conexión, cambios por enviar y antigüedad de un vistazo.
        EstadoSincronizacionBanner(
            estado = estadoSync,
            antiguedad = textoAntiguedad(estadoSync.ultimaSync ?: uiState.ultimaSincronizacion, ahora)
        )

        uiState.avisoRed?.let {
            AvisoRed(mensaje = it, pendientes = uiState.pendientes)
        }

        estadoSync.mensajeFallidas?.let { mensaje ->
            AvisoFallidas(
                mensaje = mensaje,
                revisando = uiState.soloFallidas,
                onReintentar = viewModel::reintentarFallidas,
                onRevisar = viewModel::alternarRevision
            )
        }

        EncabezadoInventario(
            fase = uiState.fase,
            sincronizando = uiState.sincronizando,
            antiguedad = textoAntiguedad(uiState.ultimaSincronizacion, ahora),
            antiguos = nuncaSincronizado || datosAntiguos(uiState.ultimaSincronizacion, ahora),
            onSincronizar = viewModel::sincronizar
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {

            when (val fase = uiState.fase) {

                ProductoUiState.Fase.Cargando ->
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        CircularProgressIndicator()

                        Text(
                            text = "Cargando inventario…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                // Base vacia y nunca sincronizada mientras baja la primera lista.
                ProductoUiState.Fase.SinProductos if nuncaSincronizado && uiState.sincronizando ->
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Descargando el inventario…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                // Primera apertura sin red: no es un error, solo no hay datos aun.
                ProductoUiState.Fase.SinProductos if nuncaSincronizado ->
                    EstadoVacio(
                        icono = Icons.Default.CloudDownload,
                        titulo = "Aún no hay datos guardados",
                        descripcion = "Este dispositivo nunca se ha sincronizado con PharmaSoft. " +
                            "Conéctate a internet y pulsa Sincronizar para descargar el inventario.",
                        modifier = Modifier.align(Alignment.Center),
                        accion = {
                            FilledTonalButton(onClick = viewModel::sincronizar) {
                                Text("Sincronizar")
                            }
                        }
                    )

                ProductoUiState.Fase.SinProductos ->
                    EstadoVacio(
                        icono = Icons.Default.Inventory2,
                        titulo = "Todavía no hay productos",
                        descripcion = "Registra el primero con el formulario de arriba.",
                        modifier = Modifier.align(Alignment.Center)
                    )

                is ProductoUiState.Fase.ConProductos ->
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = fase.productos,
                            key = { it.id }
                        ) { producto ->
                            ProductoItem(
                                producto = producto,
                                onClick = { onVerDetalle(producto.id) },
                                onDescartar = { viewModel.descartar(producto.id) }
                            )
                        }
                    }

                is ProductoUiState.Fase.Error ->
                    EstadoVacio(
                        icono = Icons.Default.CloudOff,
                        titulo = "No pudimos cargar el inventario",
                        descripcion = fase.mensaje,
                        colorIcono = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center),
                        accion = {
                            FilledTonalButton(onClick = viewModel::sincronizar) {
                                Text("Reintentar")
                            }
                        }
                    )
            }
        }
    }
}


@Composable
private fun FormularioProductoCard(
    formulario: FormularioProducto,
    registrando: Boolean,
    onNombreChange: (String) -> Unit,
    onPrecioChange: (String) -> Unit,
    onStockChange: (String) -> Unit,
    onRegistrar: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                text = "Registrar producto",
                style = MaterialTheme.typography.titleMedium
            )

            ValidatedTextField(
                value = formulario.nombre,
                onValueChange = onNombreChange,
                label = "Nombre",
                error = formulario.nombreError,
                leadingIcon = Icons.Default.Medication,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                ValidatedTextField(
                    value = formulario.precio,
                    onValueChange = onPrecioChange,
                    label = "Precio",
                    error = formulario.precioError,
                    ayuda = "En soles",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )

                ValidatedTextField(
                    value = formulario.stock,
                    onValueChange = onStockChange,
                    label = "Stock",
                    error = formulario.stockError,
                    ayuda = "Unidades",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onRegistrar,
                enabled = !registrando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (registrando) "Registrando…" else "Registrar")
            }
        }
    }
}


@Composable
private fun AvisoRed(
    mensaje: String,
    pendientes: Int
) {

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Icon(imageVector = Icons.Default.WifiOff, contentDescription = null)

            Column {
                Text(text = mensaje, style = MaterialTheme.typography.bodyMedium)
                if (pendientes > 0) {
                    Text(
                        text = if (pendientes == 1) {
                            "1 producto pendiente de enviar"
                        } else {
                            "$pendientes productos pendientes de enviar"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}


@Composable
private fun EncabezadoInventario(
    fase: ProductoUiState.Fase,
    sincronizando: Boolean,
    antiguedad: String,
    antiguos: Boolean,
    onSincronizar: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(modifier = Modifier.weight(1f)) {

            Text(
                text = "Inventario",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = antiguedad,
                style = MaterialTheme.typography.labelMedium,
                color = if (antiguos) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (fase is ProductoUiState.Fase.ConProductos) {

            val cantidad = fase.productos.size

            Text(
                text = if (cantidad == 1) "1 producto" else "$cantidad productos",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (sincronizando) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(24.dp)
            )
        } else {
            IconButton(onClick = onSincronizar) {
                Icon(imageVector = Icons.Default.Sync, contentDescription = "Sincronizar")
            }
        }
    }
}


/** «Sin conexión · 3 cambios por enviar» y «Actualizado hace 2 h». */
@Composable
private fun EstadoSincronizacionBanner(
    estado: SincronizacionUiState,
    antiguedad: String
) {
    val conexion = estado.mensajeConexion ?: return
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (estado.conectado) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (estado.conectado) Icons.Default.Sync else Icons.Default.WifiOff,
                contentDescription = null
            )
            Column {
                Text(text = conexion, style = MaterialTheme.typography.bodyMedium)
                Text(text = antiguedad, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}


/** Filas que agotaron sus intentos o fueron rechazadas: el mensaje ofrece una acción. */
@Composable
private fun AvisoFallidas(
    mensaje: String,
    revisando: Boolean,
    onReintentar: () -> Unit,
    onRevisar: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null)
                Text(text = mensaje, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReintentar) { Text("Reintentar ahora") }
                TextButton(onClick = onRevisar) { Text(if (revisando) "Ver todo" else "Revisar") }
            }
        }
    }
}


@Composable
private fun ProductoItem(
    producto: ProductoUi,
    onClick: () -> Unit,
    onDescartar: () -> Unit
) {

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {

                Icon(
                    imageVector = Icons.Default.Medication,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = producto.nombre,
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = "${producto.precio}  ·  ${producto.stock}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                producto.rechazo?.let { motivo ->
                    Text(
                        text = "No enviado: $motivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                producto.estadoEnvio?.let { estado ->
                    Text(
                        text = estado,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (producto.rechazo != null) {
                IconButton(onClick = onDescartar) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Descartar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (producto.pendiente) {

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {

                    Text(
                        text = "Pendiente",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }

            if (producto.requiereReposicion) {

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {

                    Text(
                        text = "Reponer",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }
        }
    }
}

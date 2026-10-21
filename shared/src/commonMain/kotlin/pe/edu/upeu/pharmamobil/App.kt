package pe.edu.upeu.pharmamobil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pe.edu.upeu.pharmamobil.di.ES_DEPURACION
import pe.edu.upeu.pharmamobil.presentation.diagnostico.DiagnosticoScreen
import pe.edu.upeu.pharmamobil.presentation.diagnostico.DiagnosticoViewModel

import pe.edu.upeu.pharmamobil.domain.model.Sesion
import pe.edu.upeu.pharmamobil.navigation.Screen
import pe.edu.upeu.pharmamobil.presentation.cliente.ClienteScreen
import pe.edu.upeu.pharmamobil.presentation.components.EstadoVacio
import pe.edu.upeu.pharmamobil.presentation.detalle.DetalleProductoScreen
import pe.edu.upeu.pharmamobil.presentation.inicio.InicioScreen
import pe.edu.upeu.pharmamobil.presentation.producto.ProductoScreen
import pe.edu.upeu.pharmamobil.presentation.sesion.LoginScreen
import pe.edu.upeu.pharmamobil.presentation.sesion.SesionViewModel
import pe.edu.upeu.pharmamobil.theme.PharmaMobilTheme

/** Una sola fuente para el menu lateral y el titulo de la barra superior. */
private data class Destino(
    val screen: Screen,
    val titulo: String,
    val icono: ImageVector
)

private val DESTINOS = listOf(
    Destino(Screen.Inicio, "Inicio", Icons.Default.Home),
    Destino(Screen.Productos, "Productos", Icons.Default.Medication),
    Destino(Screen.Clientes, "Clientes", Icons.Default.Person),
    Destino(Screen.Pedidos, "Pedidos", Icons.Default.ShoppingCart),
    Destino(Screen.Diagnostico, "Pruebas de sesión", Icons.Default.BugReport)
)


private val ScreenSaver = Saver<Screen, Int>(
    save = { pantalla ->
        DESTINOS.indexOfFirst { it.screen == pantalla }
    },
    restore = { indice ->
        DESTINOS[indice].screen
    }
)

/** Permisos de PharmaSoft que decide la interfaz (ver PermisosPorRol del backend). */
private const val CATALOGO_ESCRIBIR = "CATALOGO_ESCRIBIR"
private const val CLIENTES_LEER = "CLIENTES_LEER"

/**
 * Punto de entrada. Sin sesion se ve el login; con sesion, la app. El
 * composable no conoce el token: solo sabe si hay una sesion y que permite.
 */
@Composable
fun App() = KoinContext {

    val sesionViewModel: SesionViewModel = koinViewModel()
    val estado by sesionViewModel.uiState.collectAsStateWithLifecycle()
    val esDepuracion = koinInject<Boolean>(qualifier = ES_DEPURACION)

    var darkTheme by rememberSaveable {
        mutableStateOf(false)
    }

    PharmaMobilTheme(
        darkTheme = darkTheme
    ) {

        val sesion = estado.sesion

        when {

            estado.verificando ->
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

            sesion == null -> {
                val diagnostico: DiagnosticoViewModel = koinViewModel(key = "diagnostico-login-${estado.idSesion}")
                val pruebas by diagnostico.uiState.collectAsStateWithLifecycle()
                LoginScreen(
                    estado = estado,
                    onUsuarioChange = sesionViewModel::onUsuarioChange,
                    onClaveChange = sesionViewModel::onClaveChange,
                    onIngresar = sesionViewModel::iniciarSesion,
                    // Solo en depuracion: prueba del cierre de sesion completo.
                    resultadoPrueba = pruebas.resultado,
                    onProbarSinSesion = if (esDepuracion) diagnostico::peticionSinSesion else null
                )
            }

            // La clave reinicia la navegacion y los ViewModel con cada sesion nueva.
            else -> key(estado.idSesion) {
                PantallaPrincipal(
                    sesion = sesion,
                    idSesion = estado.idSesion,
                    esDepuracion = esDepuracion,
                    darkTheme = darkTheme,
                    onCambiarTema = { darkTheme = it },
                    onCerrarSesion = sesionViewModel::cerrarSesion
                )
            }
        }
    }
}


@Composable
private fun PantallaPrincipal(
    sesion: Sesion,
    idSesion: Int,
    esDepuracion: Boolean,
    darkTheme: Boolean,
    onCambiarTema: (Boolean) -> Unit,
    onCerrarSesion: () -> Unit
) {

    val puedeVerClientes = sesion.puede(CLIENTES_LEER)
    val destinos = DESTINOS
        .filter { it.screen != Screen.Clientes || puedeVerClientes }
        .filter { it.screen != Screen.Diagnostico || esDepuracion }

    var pantallaActual by rememberSaveable(stateSaver = ScreenSaver) {
        mutableStateOf<Screen>(Screen.Inicio)
    }

    // Id del producto abierto en detalle; null mientras se ve el listado.
    var productoEnDetalle by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    // Un usuario sin CLIENTES_LEER no se queda en esa seccion.
    LaunchedEffect(puedeVerClientes) {
        if (!puedeVerClientes && pantallaActual == Screen.Clientes) {
            pantallaActual = Screen.Inicio
        }
    }

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    val scope = rememberCoroutineScope()

    run {

        ModalNavigationDrawer(

            drawerState = drawerState,

            drawerContent = {

                ModalDrawerSheet {

                    DrawerHeader(sesion)

                    HorizontalDivider()

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    destinos.forEach { destino ->

                        NavigationDrawerItem(
                            label = {
                                Text(destino.titulo)
                            },
                            selected = pantallaActual == destino.screen,
                            onClick = {

                                pantallaActual = destino.screen
                                productoEnDetalle = null

                                scope.launch {
                                    drawerState.close()
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destino.icono,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.padding(
                                NavigationDrawerItemDefaults.ItemPadding
                            )
                        )
                    }

                    Spacer(
                        modifier = Modifier.weight(1f)
                    )

                    HorizontalDivider()

                    // Borra el token del almacen y devuelve al login.
                    NavigationDrawerItem(
                        label = {
                            Text("Cerrar sesión")
                        },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                            }
                            onCerrarSesion()
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.padding(
                            NavigationDrawerItemDefaults.ItemPadding
                        )
                    )

                    ModoOscuro(
                        activo = darkTheme,
                        onCambiar = onCambiarTema
                    )
                }
            }
        ) {

            Scaffold(

                topBar = {

                    TopAppBar(

                        title = {
                            Text(
                                text = if (productoEnDetalle != null) {
                                    "Detalle del producto"
                                } else {
                                    tituloDe(pantallaActual)
                                }
                            )
                        },

                        navigationIcon = {

                            if (productoEnDetalle != null) {

                                IconButton(
                                    onClick = {
                                        productoEnDetalle = null
                                    }
                                ) {

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Volver al inventario"
                                    )
                                }

                            } else {

                                IconButton(
                                    onClick = {

                                        scope.launch {

                                            drawerState.open()
                                        }
                                    }
                                ) {

                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Abrir menú"
                                    )
                                }
                            }
                        },

                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

            ) { paddingValues ->

              Column(
                  modifier = Modifier
                      .fillMaxSize()
                      .padding(paddingValues)
              ) {

                if (sesion.expirada) {
                    AvisoSesionVencidaSinRed()
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {

                    when (pantallaActual) {

                        Screen.Inicio ->
                            InicioScreen(
                                onNavegar = { destino ->
                                    pantallaActual = destino
                                },
                                puedeVerClientes = puedeVerClientes
                            )

                        Screen.Productos -> {

                            val productoId = productoEnDetalle

                            if (productoId == null) {

                                ProductoScreen(
                                    viewModel = koinViewModel(key = "productos-$idSesion"),
                                    puedeRegistrar = sesion.puede(CATALOGO_ESCRIBIR),
                                    onVerDetalle = { id ->
                                        productoEnDetalle = id
                                    }
                                )

                            } else {

                                DetalleProductoScreen(
                                    productoId = productoId,
                                    viewModel = koinViewModel(),
                                    onVolver = {
                                        productoEnDetalle = null
                                    }
                                )
                            }
                        }

                        Screen.Clientes ->
                            ClienteScreen(
                                viewModel = koinViewModel(key = "clientes-$idSesion")
                            )

                        Screen.Diagnostico ->
                            DiagnosticoScreen(
                                viewModel = koinViewModel(key = "diagnostico-$idSesion")
                            )

                        Screen.Pedidos ->
                            EstadoVacio(
                                icono = Icons.Default.ShoppingCart,
                                titulo = "Pedidos en construcción",
                                descripcion = "Este módulo llega en una próxima sesión del curso.",
                                modifier = Modifier.align(Alignment.Center)
                            )
                    }
                }
              }
            }
        }
    }
}


/**
 * Token vencido y sin red (escenario 5 de la actividad autonoma): se muestran
 * los datos locales y se avisa que al reconectar habra que iniciar sesion.
 */
@Composable
private fun AvisoSesionVencidaSinRed() {

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Icon(imageVector = Icons.Default.CloudOff, contentDescription = null)

            Text(
                text = "Tu sesión venció y no hay conexión. Ves los datos guardados; " +
                    "al reconectar tendrás que volver a iniciar sesión.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


@Composable
private fun DrawerHeader(sesion: Sesion) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {

            Icon(
                imageVector = Icons.Default.LocalPharmacy,
                contentDescription = null,
                modifier = Modifier
                    .padding(10.dp)
                    .size(28.dp)
            )
        }

        Column {

            Text(
                text = "PharmaMobil",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = sesion.nombreCompleto,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Usuario: ${sesion.usuario}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun ModoOscuro(
    activo: Boolean,
    onCambiar: (Boolean) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 28.dp,
                vertical = 16.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Icon(
            imageVector = Icons.Default.DarkMode,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Modo oscuro",
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = activo,
            onCheckedChange = onCambiar
        )
    }
}


private fun tituloDe(
    screen: Screen
): String {

    return DESTINOS.first { it.screen == screen }.titulo
}

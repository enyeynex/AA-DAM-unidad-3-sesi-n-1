package pe.edu.upeu.pharmamobil.presentation.producto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pe.edu.upeu.pharmamobil.data.repository.FakeProductoRepository
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.model.ErrorSesion
import pe.edu.upeu.pharmamobil.domain.model.FalloSesionException
import pe.edu.upeu.pharmamobil.domain.usecase.DescartarProductoUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.ObservarProductosUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.SincronizarProductosUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.UltimaSincronizacionUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.RegistrarProductoUseCase
import pe.edu.upeu.pharmamobil.platform.formatearSoles
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * viewModelScope corre sobre Dispatchers.Main, que en una prueba no existe:
 * setMain lo sustituye por un dispatcher de prueba antes de cada caso.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductoViewModelTest {

    @BeforeTest
    fun instalarMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun restaurarMain() {
        Dispatchers.resetMain()
    }

    private fun nuevoViewModel(
        repositorio: FakeProductoRepository = FakeProductoRepository()
    ) = ProductoViewModel(
        registrarProducto = RegistrarProductoUseCase(repositorio),
        observarProductos = ObservarProductosUseCase(repositorio),
        sincronizarProductos = SincronizarProductosUseCase(repositorio),
        ultimaSincronizacion = UltimaSincronizacionUseCase(repositorio),
        descartarProducto = DescartarProductoUseCase(repositorio)
    )

    @Test
    fun arrancaEnSinProductosCuandoElInventarioEstaVacio() = runTest {

        val viewModel = nuevoViewModel()

        assertEquals(ProductoUiState.Fase.SinProductos, viewModel.uiState.value.fase)
    }

    @Test
    fun muestraElInventarioConElPrecioYaFormateado() = runTest {

        val repositorio = FakeProductoRepository(
            listOf(
                Producto(id = 1L, nombre = "Paracetamol", precio = 12.5, stock = 5)
            )
        )

        val fase = assertIs<ProductoUiState.Fase.ConProductos>(
            nuevoViewModel(repositorio).uiState.value.fase
        )

        // El texto exacto depende de la plataforma: lo decide formatearSoles.
        assertEquals(formatearSoles(12.5), fase.productos.first().precio)
        assertEquals("5 u.", fase.productos.first().stock)
        assertTrue(fase.productos.first().requiereReposicion)
    }

    @Test
    fun pasaAFaseErrorSiNoSePuedeLeerLaBaseLocal() = runTest {

        val repositorio = FakeProductoRepository().apply {
            fallaAlObservar = IllegalStateException("Sin conexión")
        }

        val fase = assertIs<ProductoUiState.Fase.Error>(
            nuevoViewModel(repositorio).uiState.value.fase
        )

        assertEquals("Sin conexión", fase.mensaje)
    }

    @Test
    fun losErroresDeValidacionCaenEnElFormularioNoEnLaFase() = runTest {

        val viewModel = nuevoViewModel()

        viewModel.onNombreChange("")
        viewModel.onPrecioChange("abc")
        viewModel.onStockChange("-1")
        viewModel.registrar()

        val estado = viewModel.uiState.value

        assertEquals("El nombre es obligatorio", estado.formulario.nombreError)
        assertEquals("El precio debe ser un número válido", estado.formulario.precioError)
        assertEquals("El stock no puede ser negativo", estado.formulario.stockError)
        assertEquals(ProductoUiState.Fase.SinProductos, estado.fase)
        assertNull(estado.mensajeExito)
    }

    @Test
    fun registrarLimpiaElFormularioYLaListaSeActualizaPorElFlow() = runTest {

        val viewModel = nuevoViewModel()

        viewModel.onNombreChange("Paracetamol")
        viewModel.onPrecioChange("12.50")
        viewModel.onStockChange("5")
        viewModel.registrar()

        val estado = viewModel.uiState.value
        val fase = assertIs<ProductoUiState.Fase.ConProductos>(estado.fase)

        assertEquals("Paracetamol", fase.productos.single().nombre)
        assertEquals("", estado.formulario.nombre)
        assertEquals("", estado.formulario.precio)
        assertEquals("", estado.formulario.stock)
        assertEquals(
            "Producto \"Paracetamol\" registrado correctamente",
            estado.mensajeExito
        )
    }

    @Test
    fun alAbrirSeSuscribeYSincronizaUnaSolaVez() = runTest {

        val repositorio = FakeProductoRepository()

        nuevoViewModel(repositorio)

        assertEquals(1, repositorio.sincronizaciones)
    }

    @Test
    fun loQueTraeLaSincronizacionLlegaALaPantallaSinPedirLaLista() = runTest {

        val repositorio = FakeProductoRepository().apply {
            delServidor = listOf(Producto(id = 3L, nombre = "Ibuprofeno", precio = 4.5, stock = 20))
        }

        val fase = assertIs<ProductoUiState.Fase.ConProductos>(
            nuevoViewModel(repositorio).uiState.value.fase
        )

        assertEquals("Ibuprofeno", fase.productos.single().nombre)
    }

    @Test
    fun sinRedMuestraElAvisoYConservaLosDatosGuardados() = runTest {

        val repositorio = FakeProductoRepository(
            listOf(Producto(id = 1L, nombre = "Paracetamol", precio = 12.5, stock = 40))
        ).apply {
            fallaAlSincronizar = FalloSesionException(ErrorSesion.SinConexion)
        }

        val estado = nuevoViewModel(repositorio).uiState.value

        assertEquals("Sin conexión: mostrando datos guardados", estado.avisoRed)
        assertFalse(estado.sincronizando)
        val fase = assertIs<ProductoUiState.Fase.ConProductos>(estado.fase)
        assertEquals("Paracetamol", fase.productos.single().nombre)
    }

    @Test
    fun alReconectarSincronizarQuitaElAviso() = runTest {

        val repositorio = FakeProductoRepository().apply {
            fallaAlSincronizar = FalloSesionException(ErrorSesion.SinConexion)
        }
        val viewModel = nuevoViewModel(repositorio)

        repositorio.fallaAlSincronizar = null
        viewModel.sincronizar()

        assertNull(viewModel.uiState.value.avisoRed)
        assertEquals(2, repositorio.sincronizaciones)
    }

    @Test
    fun losProductosConIdNegativoSeMarcanComoPendientes() = runTest {

        val repositorio = FakeProductoRepository(
            listOf(
                Producto(id = -1L, nombre = "Ibuprofeno", precio = 4.5, stock = 20),
                Producto(id = 2L, nombre = "Paracetamol", precio = 12.5, stock = 40)
            )
        )

        val estado = nuevoViewModel(repositorio).uiState.value
        val fase = assertIs<ProductoUiState.Fase.ConProductos>(estado.fase)

        assertEquals(1, estado.pendientes)
        assertEquals(listOf(true, false), fase.productos.map { it.pendiente })
    }

    @Test
    fun sinRedYSinSincronizacionPreviaLaUltimaQuedaEnNull() = runTest {

        val repositorio = FakeProductoRepository().apply {
            fallaAlSincronizar = FalloSesionException(ErrorSesion.SinConexion)
        }

        val estado = nuevoViewModel(repositorio).uiState.value

        assertNull(estado.ultimaSincronizacion)
        assertEquals(ProductoUiState.Fase.SinProductos, estado.fase)
        assertEquals("Sin conexión: mostrando datos guardados", estado.avisoRed)
    }

    @Test
    fun unaSincronizacionCorrectaLlegaAlEstadoConSuInstante() = runTest {

        val repositorio = FakeProductoRepository().apply { instanteDeSincronizacion = 42_000L }

        assertEquals(42_000L, nuevoViewModel(repositorio).uiState.value.ultimaSincronizacion)
    }

    @Test
    fun unRechazadoSeCuentaAparteYSePuedeDescartar() = runTest {

        val repositorio = FakeProductoRepository(
            listOf(
                Producto(id = -1L, nombre = "paracetamol", precio = 3.0, stock = 15,
                    rechazo = "Ya existe un producto con el nombre paracetamol"),
                Producto(id = 1L, nombre = "paracetamol", precio = 12.0, stock = 0)
            )
        )
        val viewModel = nuevoViewModel(repositorio)

        assertEquals(1, viewModel.uiState.value.rechazados)
        assertEquals(0, viewModel.uiState.value.pendientes)

        viewModel.descartar(-1L)

        val fase = assertIs<ProductoUiState.Fase.ConProductos>(viewModel.uiState.value.fase)
        assertEquals(listOf(1L), fase.productos.map { it.id })
        assertEquals(0, viewModel.uiState.value.rechazados)
    }
}

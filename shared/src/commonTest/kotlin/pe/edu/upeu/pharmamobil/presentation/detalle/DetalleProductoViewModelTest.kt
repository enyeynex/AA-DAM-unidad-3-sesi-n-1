package pe.edu.upeu.pharmamobil.presentation.detalle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pe.edu.upeu.pharmamobil.data.repository.FakeProductoRepository
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.platform.FakeCompartidor
import pe.edu.upeu.pharmamobil.platform.formatearSoles
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DetalleProductoViewModelTest {

    private val paracetamol = Producto(id = 1L, nombre = "Paracetamol", precio = 12.5, stock = 5)

    @BeforeTest
    fun instalarMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun restaurarMain() {
        Dispatchers.resetMain()
    }

    private fun nuevoViewModel(
        compartidor: FakeCompartidor = FakeCompartidor(),
        repositorio: FakeProductoRepository = FakeProductoRepository(
            listOf(paracetamol))
    ) = DetalleProductoViewModel(
        repository = repositorio,
        compartidor = compartidor
    )

    @Test
    fun cargaElProductoConElPrecioYaFormateado() = runTest {

        val viewModel = nuevoViewModel()

        viewModel.cargar(1L)

        val estado = assertIs<DetalleProductoUiState.Listo>(viewModel.uiState.value)

        assertEquals("Paracetamol", estado.producto.nombre)
        assertEquals(formatearSoles(12.5), estado.producto.precio)
    }

    @Test
    fun pasaAErrorSiElProductoNoExiste() = runTest {

        val viewModel = nuevoViewModel()

        viewModel.cargar(99L)

        assertIs<DetalleProductoUiState.Error>(viewModel.uiState.value)
    }

    @Test
    fun pasaAErrorCuandoElRepositorioFalla() = runTest {

        val repositorio = FakeProductoRepository().apply {
            fallaAlObservar = IllegalStateException("Sin conexión")
        }

        val viewModel = nuevoViewModel(repositorio = repositorio)

        viewModel.cargar(1L)

        val estado = assertIs<DetalleProductoUiState.Error>(viewModel.uiState.value)

        assertEquals("Sin conexión", estado.mensaje)
    }

    @Test
    fun comparteElTextoArmadoEnCodigoComun() = runTest {

        val compartidor = FakeCompartidor()
        val viewModel = nuevoViewModel(compartidor)

        viewModel.cargar(1L)
        viewModel.compartirProductoActual()

        assertEquals(
            listOf("Paracetamol — ${formatearSoles(12.5)} · Stock: 5"),
            compartidor.textosCompartidos
        )
    }

    @Test
    fun noComparteNadaMientrasNoHayProductoCargado() = runTest {

        val compartidor = FakeCompartidor()
        val viewModel = nuevoViewModel(compartidor)

        viewModel.compartirProductoActual()

        assertTrue(compartidor.textosCompartidos.isEmpty())
    }
}

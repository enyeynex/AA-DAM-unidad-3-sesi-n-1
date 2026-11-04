package pe.edu.upeu.pharmamobil.domain.sincronizacion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pe.edu.upeu.pharmamobil.data.repository.FakeProductoRepository
import pe.edu.upeu.pharmamobil.domain.platform.MonitorDeRed
import pe.edu.upeu.pharmamobil.domain.usecase.DescartarProductoUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.ObservarEstadoSincronizacionUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.ObservarProductosUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.RegistrarProductoUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.SincronizarProductosUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.UltimaSincronizacionUseCase
import pe.edu.upeu.pharmamobil.presentation.producto.ProductoViewModel
import pe.edu.upeu.pharmamobil.presentation.sincronizacion.SincronizacionUiState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SincronizacionUiStateTest {

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun reset() = Dispatchers.resetMain()

    private class RedManual(valor: Boolean) : MonitorDeRed {
        val estado = MutableStateFlow(valor)
        override val conectado: Flow<Boolean> = estado
    }

    private fun viewModel(repo: FakeProductoRepository, red: MonitorDeRed) = ProductoViewModel(
        registrarProducto = RegistrarProductoUseCase(repo),
        observarProductos = ObservarProductosUseCase(repo),
        sincronizarProductos = SincronizarProductosUseCase(repo),
        ultimaSincronizacion = UltimaSincronizacionUseCase(repo),
        descartarProducto = DescartarProductoUseCase(repo),
        estadoSincronizacion = ObservarEstadoSincronizacionUseCase(repo),
        monitorDeRed = red
    )

    @Test
    fun elEstadoCombinaPendientesFallidasYConexion() = runTest {
        val repo = FakeProductoRepository().apply { pendientes.value = 3; fallidas.value = 2 }
        val vm = viewModel(repo, MonitorDeRed.Fijo(false))

        val estado = vm.estadoSync.first { it.pendientes == 3 }

        assertEquals(2, estado.fallidas)
        assertEquals("Sin conexión · 3 cambios por enviar", estado.mensajeConexion)
        assertEquals("2 cambios no se pudieron enviar", estado.mensajeFallidas)
    }

    @Test
    fun alRecuperarLaConexionSeSincronizaSolo() = runTest {
        val repo = FakeProductoRepository()
        val red = RedManual(false)
        viewModel(repo, red)
        val alAbrir = repo.sincronizaciones

        red.estado.value = true

        assertEquals(alAbrir + 1, repo.sincronizaciones)
    }

    @Test
    fun losMensajesSeRedactanSinIconos() {
        assertEquals("1 cambio por enviar", SincronizacionUiState(pendientes = 1).mensajeConexion)
        assertEquals("Sin conexión", SincronizacionUiState(conectado = false).mensajeConexion)
        assertNull(SincronizacionUiState().mensajeConexion)
        assertNull(SincronizacionUiState().mensajeFallidas)
    }
}

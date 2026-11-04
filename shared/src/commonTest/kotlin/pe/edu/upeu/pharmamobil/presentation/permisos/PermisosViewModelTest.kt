package pe.edu.upeu.pharmamobil.presentation.permisos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pe.edu.upeu.pharmamobil.data.repository.FakeProductoRepository
import pe.edu.upeu.pharmamobil.domain.model.Producto
import pe.edu.upeu.pharmamobil.domain.permiso.EstadoPermiso
import pe.edu.upeu.pharmamobil.domain.permiso.FakeGestorPermisos
import pe.edu.upeu.pharmamobil.domain.permiso.FakeNotificador
import pe.edu.upeu.pharmamobil.domain.permiso.decidirEstado
import pe.edu.upeu.pharmamobil.domain.port.ServicioPush
import pe.edu.upeu.pharmamobil.domain.usecase.ObservarProductosUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Los tres estados del permiso sin dispositivo: el gestor es una interfaz. */
@OptIn(ExperimentalCoroutinesApi::class)
class PermisosViewModelTest {

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun reset() = Dispatchers.resetMain()

    private val notificador = FakeNotificador()
    private val push = object : ServicioPush { override suspend fun token() = "fcm-0123456789abcdef" }

    private fun viewModel(gestor: FakeGestorPermisos, productos: List<Producto> = emptyList()) =
        PermisosViewModel(gestor, notificador, ObservarProductosUseCase(FakeProductoRepository(productos)), push)

    @Test
    fun antesDePedirElEstadoEsDenegadoYSeExplica() = runTest {
        val gestor = FakeGestorPermisos(actual = EstadoPermiso.Denegado)

        val vm = viewModel(gestor)

        assertEquals(EstadoPermiso.Denegado, vm.uiState.value.estado)
        assertEquals(0, gestor.solicitudes, "no se pide hasta que el usuario pulsa Activar")
    }

    @Test
    fun activarYConcederMuestraActivadasYPermiteProbar() = runTest {
        val gestor = FakeGestorPermisos(respuesta = EstadoPermiso.Concedido)
        val vm = viewModel(gestor, listOf(Producto(1L, "Paracetamol", 12.5, 3)))

        vm.activar()
        vm.refrescar()
        vm.probarNotificacion()

        assertEquals(EstadoPermiso.Concedido, vm.uiState.value.estado)
        assertEquals("Paracetamol necesita reposición.", notificador.mostradas.single().second)
        assertEquals("fcm-01234567…", vm.uiState.value.tokenPush)
    }

    @Test
    fun denegadoParaSiempreOfreceAjustes() = runTest {
        val gestor = FakeGestorPermisos(respuesta = EstadoPermiso.DenegadoParaSiempre)
        val vm = viewModel(gestor)

        vm.activar()
        vm.abrirAjustes()

        assertEquals(EstadoPermiso.DenegadoParaSiempre, vm.uiState.value.estado)
        assertEquals(1, gestor.ajustesAbiertos)
    }

    @Test
    fun laTablaDelPaso5DistingueLosTresEstados() {
        assertEquals(EstadoPermiso.Denegado, decidirEstado(concedido = false, yaSePidio = false, procedeExplicar = false))
        assertEquals(EstadoPermiso.Denegado, decidirEstado(concedido = false, yaSePidio = true, procedeExplicar = true))
        assertEquals(EstadoPermiso.DenegadoParaSiempre, decidirEstado(concedido = false, yaSePidio = true, procedeExplicar = false))
        assertEquals(EstadoPermiso.Concedido, decidirEstado(concedido = true, yaSePidio = true, procedeExplicar = false))
    }
}

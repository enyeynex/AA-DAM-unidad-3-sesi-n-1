package pe.edu.upeu.pharmamobil.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import pe.edu.upeu.pharmamobil.data.diagnostico.DiagnosticoSesionRest
import pe.edu.upeu.pharmamobil.db.PharmaDb
import pe.edu.upeu.pharmamobil.data.local.CreadorDeDriver
import pe.edu.upeu.pharmamobil.data.local.ProductoLocal
import pe.edu.upeu.pharmamobil.data.remote.ProductoApi
import pe.edu.upeu.pharmamobil.data.remote.ProductoApiRest
import pe.edu.upeu.pharmamobil.data.remote.crearHttpClient
import pe.edu.upeu.pharmamobil.data.repository.AuthRepositorioRest
import pe.edu.upeu.pharmamobil.data.repository.ClienteRepositorioRest
import pe.edu.upeu.pharmamobil.data.repository.ProductoRepositorioOffline
import pe.edu.upeu.pharmamobil.domain.repository.AuthRepository
import pe.edu.upeu.pharmamobil.domain.repository.ClienteRepository
import pe.edu.upeu.pharmamobil.domain.repository.ProductoRepository
import pe.edu.upeu.pharmamobil.domain.sesion.AvisosDeSesion
import pe.edu.upeu.pharmamobil.domain.sesion.DiagnosticoSesion
import pe.edu.upeu.pharmamobil.domain.usecase.CerrarSesionUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.DescartarProductoUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.IniciarSesionUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.ListarClientesUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.ObservarProductosUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.RegistrarClienteUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.RegistrarProductoUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.SesionGuardadaUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.SincronizarProductosUseCase
import pe.edu.upeu.pharmamobil.domain.usecase.UltimaSincronizacionUseCase
import pe.edu.upeu.pharmamobil.presentation.cliente.ClienteViewModel
import pe.edu.upeu.pharmamobil.presentation.detalle.DetalleProductoViewModel
import pe.edu.upeu.pharmamobil.presentation.diagnostico.DiagnosticoViewModel
import pe.edu.upeu.pharmamobil.presentation.producto.ProductoViewModel
import pe.edu.upeu.pharmamobil.presentation.sesion.SesionViewModel

/** Calificador del Boolean que dice si la app es una compilacion de depuracion. */
val ES_DEPURACION = named("esDepuracion")

/**
 * Desde la sesion 10 los repositorios hablan con PharmaSoft. Cada plataforma
 * aporta en su platformModule el motor HTTP, el AlmacenSeguro, ES_DEPURACION
 * y, desde la sesion 11, el CreadorDeDriver de la base local.
 */
val dataModule = module {
    single { crearHttpClient(motor = get(), almacen = get(), esDepuracion = get(ES_DEPURACION)) }
    single<AuthRepository> { AuthRepositorioRest(get(), get()) }
    // Offline-first: la pantalla lee de PharmaDb y la red solo la sincroniza.
    single { PharmaDb(get<CreadorDeDriver>().crear()) }
    single { ProductoLocal(get()) }
    single<ProductoApi> { ProductoApiRest(get(), get()) }
    single<ProductoRepository> { ProductoRepositorioOffline(get(), get()) }
    single<ClienteRepository> { ClienteRepositorioRest(get(), get()) }
    // Herramienta de pruebas de la actividad autonoma (solo se ofrece en depuracion).
    single<DiagnosticoSesion> { DiagnosticoSesionRest(get(), get(), get()) }
}

val domainModule = module {
    single { AvisosDeSesion() }
    factory { RegistrarProductoUseCase(get()) }
    factory { ObservarProductosUseCase(get()) }
    factory { SincronizarProductosUseCase(get()) }
    factory { UltimaSincronizacionUseCase(get()) }
    factory { DescartarProductoUseCase(get()) }
    factory { RegistrarClienteUseCase(get()) }
    factory { ListarClientesUseCase(get()) }
    factory { IniciarSesionUseCase(get()) }
    factory { CerrarSesionUseCase(get()) }
    factory { SesionGuardadaUseCase(get()) }
}

val presentationModule = module {
    viewModel { ProductoViewModel(get(), get(), get(), get(), get()) }
    viewModel { ClienteViewModel(get(), get()) }
    // El Compartidor lo aporta el platformModule de cada plataforma.
    viewModel { DetalleProductoViewModel(get(), get()) }
    viewModel { SesionViewModel(get(), get(), get(), get()) }
    viewModel { DiagnosticoViewModel(get(), get(), get(), get(ES_DEPURACION)) }
}


expect val platformModule: Module

fun initKoin(configuracionAdicional: KoinApplication.() -> Unit = {}) {
    startKoin {
        configuracionAdicional()
        modules(
            dataModule,
            domainModule,
            presentationModule,
            platformModule
        )
    }
}

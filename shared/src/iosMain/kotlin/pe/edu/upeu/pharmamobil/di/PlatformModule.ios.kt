package pe.edu.upeu.pharmamobil.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.edu.upeu.pharmamobil.data.local.CreadorDeDriver
import pe.edu.upeu.pharmamobil.data.local.CreadorDeDriverIos
import pe.edu.upeu.pharmamobil.data.local.AlmacenSeguro
import pe.edu.upeu.pharmamobil.data.local.AlmacenSeguroIos
import pe.edu.upeu.pharmamobil.domain.platform.Compartidor
import pe.edu.upeu.pharmamobil.domain.platform.MonitorDeRed
import pe.edu.upeu.pharmamobil.platform.MonitorDeRedIos
import pe.edu.upeu.pharmamobil.domain.permiso.GestorPermisos
import pe.edu.upeu.pharmamobil.domain.port.Notificador
import pe.edu.upeu.pharmamobil.permiso.GestorPermisosIos
import pe.edu.upeu.pharmamobil.permiso.NotificadorIos
import pe.edu.upeu.pharmamobil.platform.CompartidorIos
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
actual val platformModule: Module = module {
    single<Compartidor> { CompartidorIos() }
    single<MonitorDeRed> { MonitorDeRedIos() }
    single<GestorPermisos> { GestorPermisosIos() }
    single<Notificador> { NotificadorIos() }
    single<AlmacenSeguro> { AlmacenSeguroIos() }
    // Base local offline-first (sesion 11).
    single<CreadorDeDriver> { CreadorDeDriverIos() }
    single<HttpClientEngine> { Darwin.create() }
    single(ES_DEPURACION) { Platform.isDebugBinary }
}

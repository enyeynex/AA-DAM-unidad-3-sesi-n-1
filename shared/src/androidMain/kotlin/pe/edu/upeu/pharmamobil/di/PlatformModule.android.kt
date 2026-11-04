package pe.edu.upeu.pharmamobil.di

import android.content.pm.ApplicationInfo
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import pe.edu.upeu.pharmamobil.data.local.CreadorDeDriver
import pe.edu.upeu.pharmamobil.data.local.CreadorDeDriverAndroid
import pe.edu.upeu.pharmamobil.data.local.AlmacenSeguro
import pe.edu.upeu.pharmamobil.data.local.AlmacenSeguroAndroid
import pe.edu.upeu.pharmamobil.domain.platform.Compartidor
import pe.edu.upeu.pharmamobil.domain.platform.MonitorDeRed
import pe.edu.upeu.pharmamobil.platform.MonitorDeRedAndroid
import pe.edu.upeu.pharmamobil.platform.CompartidorAndroid

actual val platformModule: Module = module {
    single<Compartidor> { CompartidorAndroid(androidContext()) }
    // Guía 13: conexión para el indicador y la sincronización automática.
    single<MonitorDeRed> { MonitorDeRedAndroid(androidContext()) }
    single<AlmacenSeguro> { AlmacenSeguroAndroid(androidContext()) }
    // Base local offline-first (sesion 11).
    single<CreadorDeDriver> { CreadorDeDriverAndroid(androidContext()) }
    single<HttpClientEngine> { OkHttp.create() }
    // El registro solo se enciende en una compilacion de depuracion.
    single(ES_DEPURACION) {
        (androidContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}

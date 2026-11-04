package pe.edu.upeu.pharmamobil.domain.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * ¿Hay conexión? Lo responde cada plataforma (ConnectivityManager en Android,
 * NWPathMonitor en iOS) y se registra en su platformModule (Guía 13).
 */
interface MonitorDeRed {
    val conectado: Flow<Boolean>

    /** Para pruebas y vistas previas: una red que no cambia. */
    class Fijo(valor: Boolean = true) : MonitorDeRed {
        override val conectado: Flow<Boolean> = flowOf(valor)
    }
}

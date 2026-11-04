package pe.edu.upeu.pharmamobil.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import pe.edu.upeu.pharmamobil.domain.platform.MonitorDeRed

/** Conexión según ConnectivityManager; requiere ACCESS_NETWORK_STATE. */
class MonitorDeRedAndroid(contexto: Context) : MonitorDeRed {

    private val conectividad = contexto.getSystemService(ConnectivityManager::class.java)

    override val conectado: Flow<Boolean> = callbackFlow {
        fun hayInternet(): Boolean {
            val capacidades = conectividad.getNetworkCapabilities(conectividad.activeNetwork)
            return capacidades?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(hayInternet()) }
            override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                trySend(capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(hayInternet())
        conectividad.registerDefaultNetworkCallback(callback)
        awaitClose { conectividad.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

package pe.edu.upeu.pharmamobil.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics
import pe.edu.upeu.pharmamobil.domain.port.Telemetria

/**
 * Telemetría con Firebase Crashlytics (SDK multiplataforma de GitLive 2.7.0).
 * Es la única clase, junto con [TokenPushFirebase], que importa el SDK.
 */
class TelemetriaFirebase : Telemetria {

    override fun registrarError(e: Throwable, contexto: Map<String, String>) {
        intentar {
            val crashlytics = Firebase.crashlytics
            contexto.forEach { (clave, valor) -> crashlytics.setCustomKey(clave, valor) }
            crashlytics.recordException(e)
        }
    }

    override fun registrarEvento(nombre: String, datos: Map<String, String>) {
        intentar {
            val detalle = datos.entries.joinToString(", ") { "${it.key}=${it.value}" }
            Firebase.crashlytics.log(if (detalle.isEmpty()) nombre else "$nombre ($detalle)")
        }
    }

    /** Si Firebase no está inicializado (pruebas, iOS sin configurar), la app sigue. */
    private inline fun intentar(bloque: () -> Unit) {
        try {
            bloque()
        } catch (sinFirebase: Throwable) {
            println("Telemetría no disponible: ${sinFirebase.message}")
        }
    }
}

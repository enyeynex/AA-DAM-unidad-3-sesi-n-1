package pe.edu.upeu.pharmamobil.data.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.messaging.messaging
import pe.edu.upeu.pharmamobil.domain.port.ServicioPush

/** Token de Firebase Cloud Messaging del dispositivo. */
class TokenPushFirebase : ServicioPush {
    override suspend fun token(): String? =
        try {
            Firebase.messaging.getToken()
        } catch (sinFirebase: Throwable) {
            null
        }
}

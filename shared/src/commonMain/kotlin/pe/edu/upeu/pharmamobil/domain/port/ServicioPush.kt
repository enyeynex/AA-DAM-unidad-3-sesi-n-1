package pe.edu.upeu.pharmamobil.domain.port

/** Identificador del dispositivo para notificaciones push (Firebase Messaging). */
interface ServicioPush {
    /** null si el proveedor no está disponible (por ejemplo, sin configuración). */
    suspend fun token(): String?
}

package pe.edu.upeu.pharmamobil.permiso

import pe.edu.upeu.pharmamobil.domain.port.Notificador
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/** Notificación local inmediata con UNUserNotificationCenter. */
class NotificadorIos : Notificador {
    override fun mostrar(titulo: String, texto: String) {
        val contenido = UNMutableNotificationContent().apply {
            setTitle(titulo)
            setBody(texto)
        }
        val solicitud = UNNotificationRequest.requestWithIdentifier("stock-bajo", contenido, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(solicitud, null)
    }
}

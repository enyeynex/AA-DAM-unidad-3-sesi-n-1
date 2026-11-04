package pe.edu.upeu.pharmamobil.permiso

import kotlinx.coroutines.suspendCancellableCoroutine
import pe.edu.upeu.pharmamobil.domain.permiso.EstadoPermiso
import pe.edu.upeu.pharmamobil.domain.permiso.GestorPermisos
import pe.edu.upeu.pharmamobil.domain.permiso.Permiso
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS solo pregunta una vez: «no determinado» se puede pedir (Denegado) y
 * «denegado» ya solo se cambia en Ajustes (DenegadoParaSiempre). Las
 * notificaciones no necesitan clave en Info.plist; cámara y ubicación sí
 * (NSCameraUsageDescription, NSLocationWhenInUseUsageDescription).
 */
class GestorPermisosIos : GestorPermisos {

    private val centro = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun estado(permiso: Permiso): EstadoPermiso {
        if (permiso != Permiso.NOTIFICACIONES) return EstadoPermiso.Denegado
        val estado = suspendCancellableCoroutine { cont ->
            centro.getNotificationSettingsWithCompletionHandler { ajustes ->
                cont.resume(ajustes?.authorizationStatus ?: UNAuthorizationStatusNotDetermined)
            }
        }
        return when (estado) {
            UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional -> EstadoPermiso.Concedido
            UNAuthorizationStatusDenied -> EstadoPermiso.DenegadoParaSiempre
            else -> EstadoPermiso.Denegado
        }
    }

    override suspend fun solicitar(permiso: Permiso): EstadoPermiso {
        if (permiso != Permiso.NOTIFICACIONES) return EstadoPermiso.Denegado
        suspendCancellableCoroutine { cont ->
            centro.requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
            ) { concedido, _ -> cont.resume(concedido) }
        }
        return estado(permiso)
    }

    override fun abrirAjustes() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
            UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any?>(), null)
        }
    }
}

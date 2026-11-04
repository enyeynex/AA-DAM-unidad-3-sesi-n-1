package pe.edu.upeu.pharmamobil.permiso

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pe.edu.upeu.pharmamobil.domain.port.Notificador

/** Notificación local en el canal «stock»; sin permiso concedido no muestra nada. */
class NotificadorAndroid(private val contexto: Context) : Notificador {

    override fun mostrar(titulo: String, texto: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val gestor = contexto.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            gestor.createNotificationChannel(
                NotificationChannel(CANAL, "Stock bajo", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notificacion = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(contexto).notify(ID, notificacion)
    }

    private companion object {
        const val CANAL = "stock"
        const val ID = 1301
    }
}

# Huecos del recorrido completo · Guía 13, paso 1

Recorrido de PharmaMobil como un usuario nuevo, sobre la rama `feature/offline-first-lujan`
(sesión 11 + actividad autónoma): instalar desde cero, iniciar sesión, registrar un producto,
perder la conexión, sincronizar, provocar un error de validación y cerrar sesión.

> Confirmar cada fila con capturas del propio recorrido en el emulador antes de entregar.

| N.º | Momento | Lo observado | Gravedad | Decisión |
| --- | --- | --- | --- | --- |
| 1 | Primera apertura, sin sesión | Se muestra el login; no hay callejón sin salida. El botón «Probar petición sin sesión» solo aparece en depuración. | Baja | Descartado: el comportamiento es correcto. |
| 2 | Login con credenciales incorrectas | «Usuario o contraseña incorrectos» (401) y, sin red, «No se pudo conectar con PharmaSoft…»: los dos casos se distinguen. | Baja | Descartado: ya distingue credencial inválida de falta de conexión. |
| 3 | Listado con la base local vacía | Estado vacío propio: «Aún no hay datos guardados» con botón Sincronizar y «Nunca sincronizado». | Baja | Descartado: no parece un error. |
| 4 | Creación con el modo avión | Se guarda con id negativo y la etiqueta «Pendiente», pero el reintento depende de que el usuario pulse sincronizar; no se guarda cuántas veces falló ni por qué. | Alta | **Se cierra hoy** (pasos 2 y 3): intentos y último error por fila, espera creciente y máximo de intentos. |
| 5 | Sincronización al recuperar la señal | No hay sincronización automática: al volver la red el pendiente sigue ahí hasta pulsar 🔄. Tampoco se ve si hay conexión. | Alta | **Se cierra hoy** (paso 4): monitor de red, sincronización automática al reconectar e indicador «Sin conexión · N cambios por enviar». |
| 6 | Error de validación del servidor | Un 400/409 sobre un pendiente se muestra como aviso rojo con el motivo y «Descartar», no bajo el campo, porque el formulario ya se cerró cuando se envía. Un fallo transitorio (500) bloqueaba la fila sin límite. | Media | **Se cierra hoy** (pasos 3 y 4): fallo permanente marcado y visible con su motivo; las filas fallidas ofrecen «Reintentar ahora» y «Revisar». El mensaje bajo el campo solo aplica a registros con red (ya funciona). |
| 7 | Token vencido a mitad de un formulario | El 401 devuelve al login y lo escrito en el formulario se pierde. Lo ya guardado como pendiente no se pierde. | Media | Sesión 14: conservar el borrador del formulario al reautenticar. |
| 8 | Cierre de sesión | Se borra el token, pero la base local (productos y pendientes) queda en el equipo compartido. | Media | Sesión 14: limpiar la base local al cerrar sesión, avisando antes si quedan pendientes. |

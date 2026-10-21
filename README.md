This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

## Capacidades nativas

Sesión 9 · mecanismo `expect`/`actual` e implementaciones por plataforma. La
interfaz y el dominio siguen en `commonMain`; solo lo que depende del sistema
operativo vive en `androidMain` e `iosMain`.

| Capacidad | Mecanismo | Por qué |
|---|---|---|
| Formato de moneda (`formatearSoles`) | `expect` / `actual` | Función pura, sin dependencias: el compilador exige las dos implementaciones. |
| Compartir un producto (`Compartidor`) | Interfaz en `domain` + inyección con Koin | Necesita un `Context` en Android y un controlador de vista en iOS; en las pruebas se sustituye por un doble. |

### Archivos por plataforma

Rutas relativas a `shared/src/<sourceSet>/kotlin/pe/edu/upeu/pharmamobil/`.

| | `commonMain` | `androidMain` | `iosMain` |
|---|---|---|---|
| Formato de moneda | `platform/Formato.kt` (`expect`) | `platform/Formato.android.kt` · `java.text.NumberFormat` con `Locale("es", "PE")` | `platform/Formato.ios.kt` · `NSNumberFormatter` con `NSLocale("es_PE")` |
| Compartir | `domain/platform/Compartidor.kt` (interfaz) | `platform/CompartidorAndroid.kt` · `Intent.ACTION_SEND` + selector del sistema | `platform/CompartidorIos.kt` · `UIActivityViewController` |
| Registro en Koin | `di/AppModule.kt` (`expect val platformModule`) | `di/PlatformModule.android.kt` | `di/PlatformModule.ios.kt` |

### Dónde se usa, en código común

- `presentation/producto/ProductoUi.kt`: el mapeo `Producto.aUi()` aplica `formatearSoles`; ni el dominio ni los composables formatean precios.
- `domain/usecase/TextoParaCompartir.kt`: `Producto.comoTextoParaCompartir()` arma el texto que envían las dos plataformas.
- `presentation/detalle/`: `DetalleProductoViewModel` recibe el `Compartidor` por constructor y `DetalleProductoScreen` muestra el botón «Compartir». Al detalle se llega tocando un producto del inventario.
- Ninguna clase de `presentation` importa paquetes `android.` ni `platform.UIKit`.

### Diferencias entre plataformas

- **Precio:** cada formateador decide el espacio tras el símbolo y los separadores, por eso el texto exacto puede variar entre Android e iOS.
- **Compartir:** Android abre el selector del sistema y necesita `FLAG_ACTIVITY_NEW_TASK`, porque Koin entrega el `Context` de la aplicación y no el de una Activity. iOS presenta la hoja de compartir desde el controlador raíz de la ventana.

### Verificación

```
./gradlew :shared:testAndroidHostTest
./gradlew :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
```

El último comando compila `iosMain` también desde Windows o Linux; enlazar el framework y ejecutar en el simulador requiere macOS con Xcode.

## Autenticación

Sesión 10 · la app inicia sesión contra PharmaSoft, guarda el JWT en el almacén cifrado de cada plataforma y lo adjunta a cada petición con el plugin `Auth` de Ktor. Desde esta sesión los productos y los clientes se leen y se registran en el servidor, no en memoria.

### Antes de ejecutar

1. Levantar PharmaSoft (perfil `dev`, Oracle en `localhost:1522/FREEPDB1`). Flyway crea las tablas y los usuarios de prueba.
2. Comprobar en `http://localhost:8080/swagger-ui.html` que `POST /api/v1/auth/login` responde 200.
3. La app llega al servidor por `http://10.0.2.2:8080/api/v1/` en el emulador de Android y por `http://localhost:8080/api/v1/` en el simulador de iOS (`platform/Red.*.kt`).

### Usuarios de prueba

| Usuario | Contraseña | Rol | Qué ve en la app |
|---|---|---|---|
| `admin` | `Admin123` | ADMIN | Todo: inventario con registro y clientes. |
| `vendedor` | `Vendedor123` | VENDEDOR | Inventario solo lectura (sin formulario) y clientes. |
| `almacen` | `Almacen123` | ALMACENERO | Inventario con registro; la sección de clientes no se ofrece. |

### Piezas

| Pieza | Archivo | Qué hace |
|---|---|---|
| Sesión en el dominio | `domain/model/Sesion.kt`, `ErrorSesion.kt`, `domain/repository/AuthRepository.kt` | Credenciales, sesión con permisos (`puede()`) y errores sin hablar de HTTP. |
| Almacén seguro | `data/local/AlmacenSeguro.kt` | Interfaz común; Android usa `EncryptedSharedPreferences` (`AlmacenSeguroAndroid.kt`) e iOS el Keychain (`AlmacenSeguroIos.kt`). Se registra en cada `platformModule`. |
| Cliente HTTP | `data/remote/ClienteHttp.kt` | Plugins `ContentNegotiation`, `DefaultRequest`, `Auth` (bearer con `loadTokens` y `sendWithoutRequest`) y `Logging` con `Authorization` saneada y apagado fuera de depuración. |
| Repositorio | `data/repository/AuthRepositorioRest.kt` | Login, sesión guardada (lee `exp` del token y confirma con `/auth/me`) y cierre de sesión; llama a `clearToken()` tras cada cambio de token. |
| Errores | `data/remote/ErroresHttp.kt` | `mapearErrorSesion`: 401 → sesión expirada o credenciales inválidas, 403 → sin permiso. Un 401 fuera del login avisa por `AvisosDeSesion`. |
| Interfaz | `presentation/sesion/` y `App.kt` | `LoginScreen`, `SesionViewModel`, «Cerrar sesión» en el menú y acciones ocultas según permisos. |

### Comportamiento ante errores

- **401** (token vencido o inválido): se cierra la sesión, se borra el token y la app vuelve al login con el aviso «Tu sesión expiró».
- **403** (rol sin permiso): la sesión se mantiene y la pantalla muestra «No tienes permiso para realizar esta acción».
- **Sin conexión**: mensaje para revisar que PharmaSoft esté en ejecución.

### Verificación

```
./gradlew :shared:testAndroidHostTest
./gradlew :androidApp:assembleDebug
./gradlew :shared:compileKotlinIosSimulatorArm64
```

Las pruebas de `ClienteHttpAutenticadoTest` usan `MockEngine`: comprueban la cabecera `Bearer`, que no haya petición previa sin token, el `clearToken()` tras un segundo login, el registro saneado y el mapeo de 401 y 403 sin necesitar el servidor.

## Validación de la sesión (actividad autónoma)

Herramientas para provocar los escenarios de token de la actividad autónoma de la sesión 10:

- **Pantalla «Pruebas de sesión»** (menú lateral, solo en compilaciones de depuración): muestra si hay token en el almacén y cuándo vence, consulta el inventario con el token actual, altera un carácter de la firma del token guardado y envía el token con el prefijo `bearer` en minúscula.
- **«Probar petición sin sesión»** (pantalla de login, solo en depuración): comprueba que tras cerrar sesión el cliente HTTP ya no envía la cabecera `Authorization`.
- **`docs/matriz-permisos.ps1`**: ejecuta las seis operaciones de la matriz con `admin`, `vendedor` y `almacen` e imprime el código HTTP de cada una.

Correcciones derivadas de la bitácora:

- Al cerrar sesión, el login ya no muestra el usuario anterior.
- Un 401 a mitad de la sesión (token alterado, usuario deshabilitado) muestra el motivo que envía el servidor en lugar de «Usuario o contraseña incorrectos».

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
## Persistencia offline-first

Sesión 11. El inventario se guarda en una base SQLite local construida con
SQLDelight 2.1.0. Esa base es la única fuente de verdad de la pantalla: el
ViewModel se suscribe a ella con un `Flow` y la red solo la mantiene al día.

### Piezas

| Archivo | Responsabilidad |
| --- | --- |
| `commonMain/sqldelight/.../db/Producto.sq` | Tabla `producto` con la marca `pendiente` y sus consultas. Genera `PharmaDb`. |
| `data/local/CreadorDeDriver.kt` | Abre la base. `CreadorDeDriverAndroid` usa `AndroidSqliteDriver`; `CreadorDeDriverIos`, `NativeSqliteDriver`. Los dos guardan `pharma.db` y se registran en el `platformModule`. |
| `data/local/ProductoLocal.kt` | Consultas y conversión fila ↔ `Producto`. No importa Ktor ni los DTO. |
| `data/remote/ProductoApiRest.kt` | Llamadas a `/productos` de PharmaSoft (antes `ProductoRepositorioRest`). |
| `data/repository/ProductoRepositorioOffline.kt` | Implementa `ProductoRepository`: `observar()`, `registrar()` y `sincronizar()`. |
| `presentation/producto/ProductoViewModel.kt` | Recoge `observar()` en el `init`, sincroniza al abrir y muestra el aviso «Sin conexión: mostrando datos guardados». |

### Reglas

- **Ids locales negativos.** Un producto creado sin red se guarda con `id < 0`
  y `pendiente = 1`, y la lista lo muestra con la etiqueta «Pendiente».
- **Primero subir, después bajar.** `sincronizar()` envía cada pendiente, cambia
  su fila por la que devuelve PharmaSoft (con el id real) y solo entonces trae
  la lista del servidor. `borrarSincronizados` borra únicamente `pendiente = 0`.
- **Transacciones.** La bajada se escribe dentro de `transaction`, así el
  `Flow` emite una sola vez y la lista no parpadea.
- **Fallo de red.** `registrar()` guarda primero en local; si el envío falla con
  `ErrorSesion.SinConexion`, el producto queda pendiente sin mostrar error. Los
  demás errores (401, 403) sí se informan.
- **Sin recargas manuales.** Después de registrar no se pide la lista: el
  producto ya está en la tabla y el `Flow` emite.
- Un `Mutex` evita que dos sincronizaciones envíen el mismo pendiente dos veces.

### Pruebas

`ProductoRepositorioOfflineTest` usa una base SQLite real en memoria, distinta
en cada prueba (`baseEnMemoria()`). El driver de pruebas es un `expect` de
`commonTest`: en la JVM es `JdbcSqliteDriver(IN_MEMORY)` y en iOS el driver
nativo en memoria. Cubren el camino con red, el camino sin red, el orden
subir-bajar, que la bajada no borre pendientes y que un pendiente no se envíe
dos veces. También se actualizaron `ProductoViewModelTest` y `AppModuleTest`,
y se añadió `SincronizarProductosUseCaseTest`.

```powershell
.\gradlew :shared:testAndroidHostTest
```

En iOS, `Config.xcconfig` añade `-lsqlite3` porque el framework `Shared` es
estático y el driver nativo necesita la librería SQLite del sistema.

### Verificación del ciclo offline (emulador Android)

1. Con red, abrir la app, iniciar sesión y dejar que sincronice. Cerrar la app.
2. Activar el modo avión y volver a abrir la app: el listado aparece con el aviso
   «Sin conexión: mostrando datos guardados».
3. Registrar un producto: aparece de inmediato con la etiqueta «Pendiente».
4. Desactivar el modo avión y pulsar el botón de sincronizar.
5. En Swagger, `GET /api/v1/productos` muestra el producto con un id positivo.

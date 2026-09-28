# IPTV Player Pro

Aplicación Android profesional para reproducir contenido IPTV **de fuentes que el
usuario esté autorizado a utilizar**: listas M3U/M3U8 (archivo local o URL) y
cuentas Xtream Codes, con reproductor Media3/ExoPlayer, guía EPG (XMLTV),
VPN integrada basada en WireGuard, Material 3 y soporte de Android TV.

<p align="center"><b>TV en vivo · Películas · Series · Favoritos · Recientes · Historial · EPG · Listas · VPN · Configuración</b></p>

---

## ⚠️ Aviso de uso responsable

- La app es un **reproductor neutro**: no incluye listas, canales ni credenciales.
- Usa únicamente fuentes IPTV que estés autorizado a usar.
- La VPN integrada sirve para **privacidad** y para conectarte desde una región
  que **tu proveedor permita**. **No evita geobloqueos, DRM, autenticación,
  límites de suscripción ni ningún control de acceso** impuesto por el proveedor.
- Las cuentas vencidas se muestran como vencidas; la app **no** intenta
  reactivarlas ni manipula tokens o parámetros del servidor.

---

## Características

### 1. Fuentes IPTV
- Importación de **archivos M3U/M3U8 locales** (selector de archivos SAF).
- **URL M3U/M3U8** (con caché ETag: no se re-descarga si la lista no cambió).
- **Xtream Codes API**: servidor + usuario + contraseña (player_api.php).
- Varias listas y perfiles; lista predeterminada.
- Detección automática del tipo de fuente (M3U/M3U8/Xtream).
- Actualización manual y automática programada (WorkManager) cuando la fuente lo permite.

### 2. Organización
- TV en vivo, Películas, Series, Favoritos, Recientes, Historial.
- Canales por categoría, búsqueda global y por sección.
- Cada canal muestra nombre, logo, categoría, estado de disponibilidad
  (estado de la lista) y EPG cuando la fuente lo entrega.

### 3. Reproductor (Media3/ExoPlayer)
- HLS, MPEG-TS, MP4/MKV/WebM y demás formatos soportados por Media3.
- Pantalla completa, rotación automática según el contenido, **PiP**.
- Control de **volumen** (gesto derecha) y **brillo** (gesto izquierda).
- Avance/retroceso cuando el formato lo permite (gesto horizontal, doble toque, barra).
- Selección de **pista de audio**, **subtítulos** y **calidad**.
- **Buffer configurable** (Ajustes → Reproductor).
- **Reconexión automática** con retroceso exponencial.
- **Cambio rápido de canal** con lista lateral mientras se reproduce.
- Botón favoritos y EPG «ahora/siguiente» con barra de progreso.

### 4. VPN integrada (WireGuard · Android VpnService)
- Importa tus configuraciones `.conf` de WireGuard: pegar texto, archivo o URL.
- Conecta/desconecta desde la app (usa `GoBackend$VpnService` + `VpnService`).
- Muestra país/servidor seleccionado, estado e **IP pública** (si es consultable).
- Servidores agrupados por región; catálogo de proveedores con planes
  **gratuitos legítimos** (Proton VPN, Windscribe, PrivadoVPN, hide.me) con
  instrucciones para generar tus configs WireGuard.
- **Protección de fugas DNS**: si tu config no declara DNS, se añaden
  resolvedores públicos automáticamente.
- La app **no** enruta tu tráfico por servidores propios ni envía credenciales
  a terceros; las configuraciones se guardan cifradas con Android Keystore.

### 5. Listas vencidas
- Estado visible: Activa / **Vencida** / Error / Sin verificar, con fecha de
  expiración real reportada por la fuente.
- Al vencer: se conservan los canales ya descargados (cuando sea legal usarlos
  sin conexión), favoritos, historial, EPG almacenada y ajustes.
- Permite actualizar credenciales o agregar una nueva lista. No se falsifican
  fechas ni se manipulan tokens.

### 6. Diagnóstico de errores
- Clasificación del motivo: conexión, URL incorrecta, **cuenta vencida**,
  servidor caído, **restricción geográfica**, formato no compatible,
  autenticación o contenido protegido.
- Mensaje comprensible («No se pudo reproducir el canal») + sugerencias
  (verificar conexión, probar de nuevo, cambiar servidor VPN si el proveedor
  permite otra ubicación, revisar credenciales/estado de la lista) y botón
  **Detalles técnicos** para diagnóstico.

### 7. EPG
- XMLTV por URL, `xmltv.php` de Xtream y guía en pantalla (timeline 24 h).
- Programa actual, siguiente, barra de progreso y línea «ahora».
- Asociación automática por `tvg-id`/nombre y coincidencia manual.

### 8. Interfaz
- Material 3, modo claro/oscuro/dinámico, animaciones y skeleton loading.
- Diseño adaptable: teléfonos, tablets y **Android TV** (D-pad, foco, banner).

### 9. Mis listas
- Nombre, tipo (M3U/Xtream), cantidad de canales, última actualización,
  estado, fecha de expiración, editar/actualizar/eliminar/**probar conexión**
  y establecer predeterminada.

### 10. Rendimiento y arquitectura
- MVVM + capas (domain/data/ui), Kotlin + Compose, **Room** (caché local),
  **Paging 3** para miles de canales, **WorkManager** para refrescos,
  parseo en segundo plano (IO), Coil con caché para logos.

### 11. Seguridad
- Contraseñas Xtream y configs VPN cifradas con **Android Keystore** (AES/GCM).
- HTTPS preferido, validación de URLs, protección contra enlaces malformados.
- Nunca se registran contraseñas ni tokens en Logcat.

### 12. Actualización de la APK
- Fuente de actualización configurable: **GitHub Releases** (`api.github.com/…`)
  o JSON propio. Muestra versión actual/nueva y cambios.
- Descarga con **DownloadManager** e instalación mediante el **instalador
  oficial** de Android (jamás silenciosa).

Formato del JSON propio:
```json
{
  "versionName": "1.1.0",
  "versionCode": 2,
  "changelog": "— Nuevo reproductor\n— Correcciones",
  "apkUrl": "https://tuservidor.com/iptv-player-pro-1.1.0.apk"
}
```

---

## Compilación

```bash
git clone https://github.com/municipalidad1998/TelegramBackup.git
cd TelegramBackup
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requisitos: JDK 17, Android SDK 35. La compilación corre también en GitHub
Actions (`.github/workflows/build.yml`).

## Compatibilidad
- Android 8.0 (API 26) o superior.
- Teléfonos, tablets y Android TV / Google TV.
- Optimizada para Wi-Fi y datos móviles (buffer configurable, reconexión).

## Estructura del proyecto

```
app/src/main/java/com/iptvplayerpro/
├── core/          # utilidades (URLs, fechas), criptografía Keystore, ajustes DataStore
├── data/
│   ├── local/     # Room: entidades, DAOs, base de datos
│   ├── remote/    # OkHttp, Xtream (Retrofit), parser M3U, parser XMLTV
│   └── repository/# implementaciones de los repositorios
├── domain/        # modelos y contratos de repositorios
├── di/            # contenedor de dependencias + helper de ViewModels
├── player/        # ExoPlayer, clasificador de errores de reproducción
├── vpn/           # WireGuard: controlador, inspector de configs, catálogo
├── work/          # WorkManager: refresco de listas y EPG
└── ui/            # Compose: tema, navegación, componentes y pantallas
```

## Licencias de terceros
- WireGuard for Android (tunnel) — Apache 2.0
- Media3/ExoPlayer, AndroidX, Coil, OkHttp, Retrofit — Apache 2.0

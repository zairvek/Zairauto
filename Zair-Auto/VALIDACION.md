# Validación de Zair Auto 0.2.3

Fecha: 15 de octubre de 2026. Versión 0.2.3-zair, código 5.

- Reproducido en emulador Android 11 x86_64 (APK x86_64 de la misma fuente): al abrir Configuración
  la app se cerraba con `RuntimeException: Addon not found: whisper`. Tras la corrección abren sin cierre
  Configuración, sus 10 secciones y todas las opciones del menú lateral (Inicio, Biblioteca, Favoritos,
  Listas, TV, YouTube, Navegador, Agregar carpeta, Abrir enlace, Sonido, Compartir informe).
- No probado aún en teléfono real ARM64 ni en Android Auto.

- `fermata:packageAutoReleaseUniversalApk -PZAIR_CORE=true -PABI=arm64-v8a`: compilación correcta,
  con R8 y `lintVitalAutoRelease` aprobados.
- `verification/run-checks.sh`: 20 casos de motores/enlaces, 113 de geometría, XML válidos y claves
  español/inglés de Zair coincidentes.
- Traducciones al español comprobadas contra el inglés: mismos nombres, mismos marcadores de formato
  (`%1$s`, `%d`…) y XML válido en los 8 archivos.
- El APK compilado aquí está firmado con una clave de prueba distinta de la de 0.2.1.

---

# Validación de Zair Auto 0.2.1

Fecha: 14 de octubre de 2026. Base Fermata 2.0.2. Versión 0.2.1-zair, código 3.

## Aprobado

- Compilación de lanzamiento `fermata:packageAutoReleaseUniversalApk`, con ZAIR_CORE y ARM64. R8 y lintVitalAutoRelease aprobados.
- 20 comprobaciones de regresión de motores/enlaces y 113 comprobaciones de geometría.
- 179 XML de app, 40 de utils y 79 de módulos válidos. Claves de traducción español/inglés coincidentes.
- git diff --check sin errores.
- Integridad ZIP del APK comprobada. ABI: arm64-v8a.
- Firma APK y alineación de 16 KB verificadas. Mismo certificado que 0.1.0 y 0.2.0.

APK: Zair-Auto-0.2.1-arm64.apk, 62331657 bytes.
SHA-256: 91299d22292635f91de0229b267e95f228f2721b06514f26ae03dcabd35ca2d2.
Certificado SHA-256: 0780e355682255b04eb77ee5cafe6ab8bc3c74f8b9dcf646288cafd83797dd8c.

## Cierre comunicado por el usuario

El usuario informó de que la app no respondió al tocar una opción; no se identificó
cuál opción ni se obtuvo una traza del teléfono. Se encontraron y corrigieron defectos
de navegación, menús durante carga y el manejador heredado que podía dejar muerto el
hilo principal sin terminar el proceso. No se puede atribuir todavía el bloqueo concreto
a uno de esos defectos ni afirmar que haya desaparecido en su dispositivo.

Se intentó reproducir en un emulador AOSP Android 15 sin aceleración de CPU. QEMU
notificó hilos bloqueados y terminó antes de completar el arranque; no se ejecutó
la app. No hay prueba funcional aprobada en emulador, teléfono real ni vehículo.
El fallo de QEMU no es evidencia de un fallo de Zair Auto.

0.2.1 añade reporte local de excepciones, estado del hilo principal durante bloqueos
y motivos de salida de Android. El usuario lo exporta desde Menú → Compartir informe
del fallo. No se envía a servidores ni al autor del proyecto original.

## Android Auto: alcance comprobado por código

La foto del usuario corresponde al navegador del servicio multimedia de audio. En esa
ruta `DefaultMediaLib.getChildren` ofrece Carpetas, Favoritos, Listas y módulos de
biblioteca como TV. YouTube es un FermataFragmentAddon y no es MediaLibAddon: no forma
parte de ese árbol. El host Android Auto dibuja su propia pantalla en esta ruta.

El servicio se etiqueta ahora Zair Auto · Audio para distinguirlo del servicio completo.
La interfaz propia de MainCarActivity usa ahora Inicio, carrusel y menú de proyección
con YouTube. Se mantienen sus limitaciones de acceso: este cambio no desbloquea la
proyección ni permite sustituir por CSS/vistas el navegador de audio de Android Auto.
No se probó que el servicio completo sea accesible en el coche del usuario.

No se probaron servicios de YouTube, login, streaming, IPTV, Cast ni reproducción real.
Video en vehículos para uso estacionado. La compilación no sustituye las pruebas reales.

El archivo fuente incluye el mapping R8 de esta versión para interpretar informes.

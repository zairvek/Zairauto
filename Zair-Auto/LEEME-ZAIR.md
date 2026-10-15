# Zair Auto 0.2.3 — versión de prueba

Aplicación Android nativa basada en **Fermata Media Player**, de Andrey Pavlenko.
Este es un fork independiente: no es una versión oficial ni está afiliado al autor.

## Instalar

1. Instala `Zair-Auto-0.2.3-arm64.apk` en tu teléfono Android 9 o superior.
2. Permite instalar desde la aplicación con la que abriste el APK, si Android lo pide.
3. Abre **Zair Auto**, pulsa **Agregar carpeta** y selecciona una carpeta con música o video.
4. Para TV/IPTV utiliza el módulo TV con tus propias listas. No incluye suscripciones ni canales.
5. **Abrir enlace multimedia** acepta enlaces directos HTTP/HTTPS, por ejemplo MP4 o M3U8. Para YouTube usa su módulo.

Se instala junto a Fermata, con datos propios. No importa automáticamente su configuración.
El APK ARM64 sirve para teléfonos Android modernos de 64 bits. No incluye x86 ni ARM de 32 bits.

## Corrección 0.2.3

- **Configuración cerraba la app** («Addon not found: whisper»). La edición compacta
  (`ZAIR_CORE`) no incluye el motor de voz Whisper, pero Configuración intentaba cargarlo
  para «Subtítulos automáticos». Ahora esa sección solo aparece si el motor está incluido,
  y un módulo ausente nunca cierra la app (tampoco en los ajustes de cada carpeta o video).
- Comprobado en un emulador Android 11: Configuración y sus 10 secciones, y todas las
  opciones del menú lateral, abren sin cerrarse.

## Correcciones y mejoras 0.2.2

- **Compartir informe del fallo** ya no puede cerrar la app en la variante `mobile`
  (no tiene FileProvider): el informe se comparte como texto. También avisa si no hay
  ninguna app para compartir en lugar de fallar.
- **Biblioteca vacía**: tocar la pantalla vacía de Carpetas vuelve a abrir «Agregar carpeta»
  (antes el toque nunca llegaba porque RecyclerView no llama a `performClick`).
- El mensaje «Todavía no hay elementos» ya no aparece mientras una carpeta se está cargando,
  muestra «Sin resultados» al buscar y la pista de agregar carpeta solo en la raíz de Carpetas.
- **Portadas**: el elemento enfocado (cruceta / mando giratorio del coche) y el que suena
  ahora se resaltan; antes el fondo transparente los ocultaba.
- Al volver de portadas a lista se restauran fondo, desplazamiento y elevación de la lista.
- **Atrás** sale primero del video a pantalla completa aunque se haya abierto desde Inicio.
- **Abrir enlace multimedia** acepta enlaces copiados con espacios.
- Inicio muestra el título de lo que suena y ya no se recarga dos veces al abrirse.
- Menos trabajo al desplazar portadas: no se crean dibujables ni pinceles en cada fotograma.
- **Español completo**: se rehízo la traducción de la app (la anterior era parcial y automática,
  con errores como «Juega a continuación al finalizar») y se tradujeron los módulos
  TV, Web/YouTube, Felex, Chat, POI y utilidades. Nuevos comandos de voz en español
  («pon …», «busca en youtube …», «adelanta 30 segundos», «pausa»…).

Ninguna opción se ha quitado.

## Correcciones 0.2.1

- Acceso del servicio de audio renombrado a **Zair Auto · Audio**.
- Inicio y carrusel compartidos con la interfaz completa `MainCarActivity` de Fermata.
- Navegación de YouTube recuperada: ya no oculta permanentemente la barra superior.
- Transiciones desde el menú diferidas hasta después de cerrar el diálogo.
- Protección de menús de ordenación antes de cargar los elementos y de navegación tras guardar el estado.
- Sustituido el manejador de errores heredado que podía dejar vivo el proceso con el hilo principal terminado.
- Nuevo **Compartir informe del fallo** en el menú del teléfono: captura local de excepciones, bloqueo del hilo principal y motivos de cierre de Android. Solo se comparte al pulsarlo.

El bloqueo exacto comunicado por el usuario no se ha reproducido aquí. Esta es una
actualización de correcciones y diagnóstico; no certifica que ese bloqueo haya desaparecido.
Si se repite, vuelve a abrir la app y usa **Menú → Compartir informe del fallo**.

## Diseño conservado de 0.2.0

- Interfaz reconstruida como un carrusel horizontal de portadas, inspirado en la referencia del usuario.
- Fondo oscuro azul/negro, perspectiva suave al deslizar y títulos centrados en dos líneas.
- Biblioteca, favoritos, listas y secciones multimedia compatibles con portadas verticales.
- Menú lateral con biblioteca, YouTube, TV, favoritos, listas, enlaces, ecualizador y ajustes.
- Barra superior reducida a menú y búsqueda; las opciones secundarias pasan al menú lateral.
- Opción «Portadas / lista» para cambiar de presentación en la biblioteca.
- Carátulas reales del archivo cuando están disponibles; ilustraciones propias si no hay portada.
- Inicio con la última reproducción y accesos a las principales secciones.
- Resultados de búsquedas anteriores descartados también al ejecutar la actualización en pantalla.
- Reconocimiento de carátulas integradas y protección ante descripciones multimedia nulas.
- Botón Atrás protegido mientras carga la biblioteca; retorno de las secciones al Inicio.
- Cierre del teclado al salir de la búsqueda y cierre de diálogos al destruir la pantalla.
- Conserva las correcciones de audio sin MIME, eventos tardíos del motor, enlaces externos y actualizaciones del upstream de 0.1.0.

El APK conserva el identificador y la clave privada de la primera entrega, con un código
de versión mayor. Puede instalarse como actualización de Zair Auto 0.1.0 y 0.2.0.
0.2.3 usa el código de versión 5: para instalarlo como actualización tiene que firmarse con
esa misma clave (ver «Compilar el código»).
No incluye las portadas de videojuegos de la referencia ni archivos multimedia de muestra.

Se conservan los motores MediaPlayer, ExoPlayer y VLC, IPTV, YouTube/navegador,
favoritos, listas, controles multimedia, integración Android Auto y los módulos
de red y Cast del código original. Su presencia no implica que todos se hayan
probado con cada servicio, formato, teléfono y automóvil.

La compilación `ZAIR_CORE` omite únicamente los módulos opcionales de transcripción
Whisper y traducción ML Kit/OpusMT para no descargar modelos ni compilar código
nativo de voz. No son necesarios para música, video, TV ni Android Auto. Sus
fuentes se incluyen completas; quita `-PZAIR_CORE=true` para compilarlos.

## Android Auto

Hay dos accesos distintos:

- **Zair Auto · Audio**: Android Auto dibuja la biblioteca y el reproductor de audio. No muestra la web de YouTube ni admite el carrusel personalizado de esta app.
- **Zair Auto** (proyección completa de Fermata): renderiza la interfaz propia con Inicio, carrusel, menú y YouTube si el módulo está activo. Solo sirve cuando Android Auto permite abrir ese servicio en tu instalación.

Cambiar el nombre del acceso o mejorar el diseño no convierte el navegador de audio
en la interfaz completa y no elimina bloqueos de Android Auto. No se añadieron
categorías falsas ni cambios para eludir la validación de Android Auto.


Una APK instalada manualmente puede no aparecer en la pantalla del carro.
La disponibilidad depende de Android, Android Auto, el método de instalación y
el equipo del vehículo. La app no elimina esas restricciones ni garantiza
compatibilidad con Android 14/15/16 o con cada pantalla. El video en un vehículo
es para uso estacionado.

Los cambios de apariencia no corrigen por sí solos restricciones de proyección,
DRM, cambios de YouTube ni problemas de los servidores IPTV.

## Compilar el código

Requisitos: JDK 17 completo, Android SDK 36 y Build Tools 36.0.0. Python 3 para las comprobaciones auxiliares. El wrapper
incluido descarga Gradle 9.7.1 y las dependencias definidas por el proyecto base.

Define `ANDROID_HOME` apuntando a tu Android SDK o configura `sdk.dir` en
`local.properties` desde Android Studio. Después, desde la raíz:

```bash
./gradlew fermata:packageAutoReleaseUniversalApk -PZAIR_CORE=true -PABI=arm64-v8a -PAPP_NAME="Zair Auto"
```

En Windows utiliza `gradlew.bat`. La APK universal, con los módulos incluidos,
se genera en `fermata/build/outputs/apk_from_bundle/autoRelease/`.
Una APK base obtenida solamente con `assembleAutoRelease` no es equivalente:
puede omitir los módulos dinámicos.

Los identificadores Android son independientes del proyecto original:
`dev.zair.auto.auto`. Los paquetes Java originales se conservan por compatibilidad.
El proyecto crea una clave local de prueba si no se configura una firma propia.
Para distribuir actualizaciones utiliza siempre la misma clave y un `versionCode`
mayor. Conserva tu keystore de forma privada. Las claves y rutas de este entorno
de compilación no se incluyen en el código fuente distribuido. El respaldo
privado de la firma entregada va por separado en `Zair-Auto-Respaldo-de-firma.zip`.
Consérvalo para poder instalar futuras versiones sobre esta APK.

### Compilación automática en GitHub

El flujo `.github/workflows/zair-auto.yml` de la raíz del repositorio compila el APK en cada
cambio de `Zair-Auto/` y lo publica como `Zair-Auto.apk` en la versión `zair-auto` de Releases.
Para que se instale como actualización de tu APK, añade en *Settings → Secrets → Actions*
`CI_KEYSTORE` (tu keystore en base64), `CI_ALIAS`, `CI_KEY_PWD` y `CI_KEYSTORE_PWD`.
Sin esos secretos el APK se firma con una clave nueva y Android pedirá desinstalar la versión anterior.

## Comprobaciones

```bash
bash verification/run-checks.sh
```

Comprueba 20 casos de motores, URLs y audio, más 113 comprobaciones de geometría responsive,
la sintaxis XML y que las cadenas añadidas estén en español e inglés.
El resultado de compilación y las limitaciones de las pruebas de este paquete
se describen en `VALIDACION.md`.

## Licencia y origen

GPL-3.0, copia incluida en `LICENSE`. El código fuente completo debe acompañar
a las versiones modificadas distribuidas bajo los términos de esa licencia.
Los derechos y créditos originales se mantienen. Cambios Zair Auto: 14/10/2026.

Repositorio original: https://github.com/AndreyPavlenko/Fermata

Commit base: `7d2bd42e0fea98a796ba069b56c48ef07d874549` (versión base 2.0.2).
Consulta `CAMBIOS-ZAIR.md` para localizar cada modificación.

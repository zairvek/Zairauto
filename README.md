# Zairauto

Reproductor multimedia para **Android Auto** inspirado en [Fermata Auto](https://github.com/AndreyPavlenko/Fermata),
reescrito desde cero en Kotlin con **Jetpack Compose (Material 3)** y **Media3 / ExoPlayer**.

## Qué mejora respecto a Fermata

| Problema en Fermata | Zairauto |
|---|---|
| Interfaz antigua y densa, ajustes difíciles de encontrar | Interfaz Material 3 / Material You, tema claro/oscuro/negro puro, 4 pestañas claras |
| Pide acceso a **todo** el almacenamiento | Usa el selector de carpetas de Android (SAF): solo lee las carpetas que eliges |
| Se rompe o desaparece de Android Auto tras actualizaciones | Usa la **API oficial** de apps multimedia (`MediaLibraryService`), la misma que Spotify |
| Configurar Android Auto es confuso | Guía paso a paso integrada, con botón que abre los ajustes de Android Auto |
| Escaneo lento en carpetas grandes | Lee carpetas con `DocumentsContract` (una consulta por carpeta) + caché + índice en memoria |
| Orden "Pista 1, Pista 10, Pista 2" | Orden natural: 1, 2, … 10 |
| Un archivo dañado para la música | Salta automáticamente al siguiente archivo reproducible |
| Pierdes la posición del audiolibro | Recuerda la posición de cada archivo de más de 15 min y la última cola tras reiniciar |
| Sin carátulas en el coche | Carátulas incrustadas y `cover.jpg`/`folder.jpg` visibles también en Android Auto |

## Funciones

- **Android Auto**: Biblioteca, Favoritos, Recientes y Radio como pestañas; búsqueda; voz ("Ok Google, pon *X* en Zairauto");
  botones de favorito, aleatorio, repetir y retroceder 10 s; vista en lista o cuadrícula.
- **Biblioteca**: varias carpetas (memoria interna, SD, USB), búsqueda sin acentos, "Mezclar todo", reproducir/aleatorio por carpeta.
- **Radio**: emisoras por URL (MP3/AAC/HLS), importación de listas M3U/PLS desde archivo o URL.
- **Reproductor**: cola editable, velocidad 0,5×–2,5×, temporizador de apagado con fundido o "al terminar la pista",
  saltar silencios, vídeo en el móvil (en el coche solo audio).
- **Ecualizador**: bandas, presets, refuerzo de graves y amplificador de volumen. Compatible con ecualizadores externos.

## Instalación

1. Descarga **[Zairauto.apk](../../releases/latest/download/Zairauto.apk)** (también en la sección **Releases** del repositorio) o compílalo:
   ```bash
   ./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
   ```
2. Abre la app → aparece la tarjeta **"Úsala en tu coche"** con los pasos para Android Auto:
   activar el modo desarrollador de Android Auto (tocar 10 veces "Versión") y habilitar **Fuentes desconocidas**.
   Esto es necesario para cualquier app que no venga de Play Store.

> El APK de release está firmado con la clave de depuración para que se pueda instalar sin más.
> Si lo vas a distribuir, configura tu propia clave en `app/build.gradle.kts`.

## Lo que no hace (a propósito)

Fermata permite ver vídeo y navegar por webs en la pantalla del coche usando APIs no oficiales de Android Auto.
Zairauto no lo hace: esas APIs se rompen con cada actualización de Google y mostrar vídeo en marcha es peligroso.
Los vídeos se ven en el móvil y en el coche suenan como audio.

## Estructura

```
app/src/main/java/com/zairvek/zairauto/
├── data/        Biblioteca, ajustes, escáner de carpetas, parser M3U
├── playback/    PlaybackService (Media3), árbol de Android Auto, ecualizador, etiquetas
├── provider/    ArtworkProvider: carátulas e iconos para Android Auto
└── ui/          Compose: Inicio, Biblioteca, Radio, Ajustes, reproductor
```

Requisitos: Android 8.0+ (API 26). Compila con JDK 17+ y Android SDK 35.

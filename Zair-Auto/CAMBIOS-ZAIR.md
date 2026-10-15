# Cambios del fork — 14 de octubre de 2026

Autor de los cambios: Zair Auto contributors. Licencia: GPL-3.0.
Autor del proyecto base: Andrey Pavlenko y colaboradores.

| Archivo o grupo | Modificación |
| --- | --- |
| `ZairHomeFragment.java` | Nuevo inicio Android nativo conectado al reproductor y módulos existentes. |
| `ZairPlaybackGuard.java` | Validación de enlaces y filtro por identidad del motor activo. |
| `IntentPlayable.java` | Detección de audio por la ruta cuando Android no proporciona MIME para enlaces HTTP. |
| `MediaSessionCallback.java` | Rechazo de callbacks tardíos y protección al actualizar el estado sin fuente. |
| `MainActivityDelegate.java` | Registro de Inicio, navegación inicial en móvil, resultado de intents multimedia y desactivación de acciones de actualización del upstream. |
| `MainActivityPrefs.java` | Tema oscuro predeterminado y menos controles visibles por defecto. |
| `NavBarMediator.java` | Acceso a Inicio en móvil y créditos de fork en Acerca de. |
| `values/theme*.xml` | Colores, contraste, tipografía, tamaños de controles y esquinas. |
| `layout/main_activity.xml`, `layout/media_item_view.xml` | Etiquetas de navegación, visibilidad del botón flotante y espacio entre elementos. |
| `values/ids.xml`, `values*/zair_strings.xml` | Identificador de Inicio y textos español/inglés. |
| `drawable*/launcher.xml`, `drawable/zair_*.xml` | Icono vectorial de la aplicación y del acceso Inicio. |
| `fermata/build.gradle`, `gradle/libs.versions.toml` | Nombre, applicationId y versión independiente. |
| `settings.gradle` | Opción `ZAIR_CORE` para omitir tres módulos opcionales de voz/traducción. |
| `res/build_jks.gradle` | Claves de prueba aleatorias para cada entorno, en lugar de derivarlas de contraseñas públicas. |
| `verification/` | Comprobaciones ejecutables sin Android y validación XML. |
| `LEEME-ZAIR.md`, `CAMBIOS-ZAIR.md`, `VALIDACION.md` | Uso, compilación, trazabilidad y alcance real de pruebas. |

El resto de funciones procede del proyecto base; no se presenta como desarrollo nuevo.

## Versión 0.2.0 — reconstrucción visual

- `ZairHomeFragment`: reemplazo del dashboard por una estantería de portadas nativa.
- `ZairCoverFlow`, `ZairPosterDrawable`, `ZairCoverGeometry`: perspectiva, ilustraciones propias y medidas adaptables.
- `ZairNavigation`, `ZairToolBarMediator`: menú lateral, búsqueda y acceso a acciones secundarias.
- `MediaItemListView`, `MediaItemView`: estantería horizontal, títulos y carátulas reales.
- `MediaItemListViewAdapter`: descarte de resultados antiguos dentro de la actualización pendiente de interfaz.
- `MainActivityDelegate`, `MainActivityFragment`, `MediaLibFragment`, `NavBarMediator`, `FloatingButtonMediator`: navegación simplificada y retorno al Inicio en teléfono.
- Temas, fondos y cadenas español/inglés actualizados.
- `verification/ZairCoverGeometryTest.java`: matriz de tamaños, orientación y fuentes grandes.

Se conserva la navegación de Android Auto del código base y su restricción de compatibilidad.

## Versión 0.2.1 — navegación y diagnóstico

- `MainActivityDelegate`, `MainActivityFragment`, `MediaLibFragment`, `MainActivityPrefs`, `NavBarMediator`, `FloatingButtonMediator`, `MediaItemListView`, `MediaItemView`: Inicio y carrusel también en `MainCarActivity`; guardas de estado y callbacks de navegación. No afectan a la interfaz de MediaBrowser dibujada por Android Auto.
- `ZairNavigation`: menú de proyección basado en OverlayMenu; acciones del menú de teléfono diferidas tras cerrar el diálogo; explicación de ambos accesos e informe compartible.
- `YoutubeFragment`: recuperación de barra de navegación y preparación de recursos del módulo antes de inflar su vista.
- `ToolBarMediator`, `ZairToolBarMediator`: no acceder a controles o padres de biblioteca aún no disponibles.
- `ZairDiagnostics`, `FermataApplication`, `MainActivity`, `filepaths.xml`: reporte local acotado, motivos de salida Android y detector de bloqueo en primer plano. Exportación manual mediante FileProvider limitado a su carpeta de caché.
- El manejador de excepciones del delegado ya no reemplaza el del sistema; se elimina el correo de informes del autor original en este fork. El cierre exacto comunicado por el usuario sigue sin reproducirse.
- `fermata/build.gradle`: servicio genérico etiquetado Zair Auto · Audio.
- Versión 0.2.1-zair, código 3, certificado de firma conservado.

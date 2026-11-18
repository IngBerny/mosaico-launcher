# Mosaico: launcher para Android con estilo Windows 10 Mobile

Launcher nativo (Kotlin, sin librerías externas) que recrea la experiencia de Windows 10 Mobile:
Live Tiles, lista de apps alfabética y Centro de actividades.

## Qué incluye

**Pantalla de Inicio**
- Mosaicos pequeños, medianos, anchos y grandes en cuadrícula de 4 columnas (o 6 con "Mostrar más mosaicos").
- Mantén presionado un mosaico para entrar al modo edición: arrástralo para moverlo, ⊘ para desanclarlo y ↘ para cambiarle el tamaño.
- Efecto de inclinación (tilt) al tocar y animación de giro (turnstile) al abrir apps.
- Contadores de notificaciones en los mosaicos (99+), como en el original.
- Tres estilos de fondo: fondo de pantalla de Android con mosaicos translúcidos, una foto tuya *dentro* de los mosaicos (el efecto de W10M) o color sólido.

**Mosaicos dinámicos (Live Tiles)**
- **Calendario:** día y fecha, y tu próximo evento; se voltea entre las dos caras.
- **Clima:** temperatura actual y pronóstico de 3 días (Open-Meteo, sin clave de API), con íconos de línea.
- **Fotos:** pasa tus fotos recientes con fundido.
- **Batería:** porcentaje y estado de carga.

**Lista de apps** (desliza a la izquierda desde Inicio)
- Orden alfabético con encabezados; toca una letra para abrir la cuadrícula A–Z.
- Buscador y sección "Agregadas recientemente".
- Mantén presionada una app para anclarla a Inicio, ver su información o desinstalarla.

**Centro de actividades** (desliza hacia abajo en Inicio)
- Acciones rápidas: Wi-Fi, Bluetooth, linterna, rotación, ubicación, modo avión, horas de silencio, ahorro de batería, datos, zona Wi-Fi, cámara y configuración.
- Notificaciones agrupadas por app: tócalas para abrirlas, deslízalas para descartarlas o usa "Borrar todo".

**Personalizar**
- Tema oscuro o claro, los 20 colores de énfasis originales o un color libre (tono, saturación, brillo o código hex).
- Color propio para cada mosaico (énfasis, color de la app, transparente, cualquiera de la paleta o uno libre), además de cambiarle el nombre u ocultarlo. En modo edición, toca el botón de paleta.
- Paquetes de íconos de Google Play (formato ADW/Nova), íconos monocromáticos y tamaño de íconos ajustable.
- Ocultar los nombres de todos los mosaicos, espacio entre mosaicos y 4 o 6 columnas.
- Bloquear el diseño de Inicio para no moverlo por accidente.
- Ocultar apps de la lista.

**Launcher predeterminado**
- Al abrirlo por primera vez aparece el diálogo de Android "¿Usar Mosaico como app de inicio?".
- Si todavía no es el predeterminado, Inicio muestra un aviso con el botón "Establecer".

## Compilar e instalar

Requiere Android 10 o superior.

### Opción A: Android Studio
1. Abre la carpeta `mosaico` con **File → Open**.
2. Espera a que Gradle sincronice, conecta el teléfono con depuración USB y presiona **Run ▶**.
3. Para obtener el APK: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

### Opción B: GitHub (sin instalar nada)
1. Sube esta carpeta a un repositorio de GitHub.
2. El flujo `.github/workflows/build.yml` compila automáticamente en cada push.
3. Ve a **Actions → Compilar APK → (última ejecución) → Artifacts → mosaico-apk**, descarga el zip e instala el APK en el teléfono.

### Ponerlo como launcher predeterminado
Mosaico lo pide solo la primera vez. También puedes tocar el aviso "Establecer" en Inicio o ir a
**Personalizar → Elegir launcher predeterminado**. Si tu marca bloquea el diálogo, se abre directamente
**Ajustes → Apps → Apps predeterminadas → App de inicio**.

### Firma
El APK se firma con `app/mosaico-release.jks` (contraseña en `app/keystore.properties`) para que cada
versión se instale encima de la anterior. Si algún día publicas la app en Google Play, crea una clave nueva y privada.

## Primeros pasos recomendados
- En **Personalizar → Permisos**, activa el acceso a **Notificaciones** (contadores y Centro de actividades).
- Toca los mosaicos de Calendario, Clima y Fotos para darles permiso. Para el clima también puedes escribir tu ciudad.

## Limitaciones de Android
- La pantalla de bloqueo, la barra de estado y la barra de navegación son del sistema; un launcher no las puede reemplazar.
- Los íconos son los de cada app. Con "Íconos monocromáticos", las apps compatibles muestran su versión blanca, como en W10M.
- La rotación y "horas de silencio" necesitan permisos especiales que la app te pide la primera vez.

Proyecto independiente, sin relación con Microsoft. No usa logotipos ni marcas de Microsoft.

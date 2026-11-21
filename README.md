# Mosaico: launcher para Android, al estilo POCO Launcher

Launcher nativo (Kotlin, sin librerías externas), al estilo de POCO Launcher y con mucha personalización.
Su tema por defecto, **Mirador**, usa tres colores (tinta, crema y salvia), letras finas y un fondo de montañas
al anochecer que cambia con tu color de énfasis.

## Qué incluye

**Pantalla de inicio**
- Saludo con tu nombre ("Buenas noches, Berny"), hora grande y fina (o mediana, apilada u oculta) y fecha.
- Una línea discreta con el clima, la batería y tu próximo evento.
- Lo que está sonando (Spotify, YouTube Music…) con controles de anterior, pausa y siguiente.
- Apps en cuadrícula con nombres (3 a 7 columnas, tamaño de íconos y de nombres, sombra), o en lista de texto,
  lista con íconos o solo íconos. Puntos de color cuando una app tiene notificaciones.
- Dock con hasta 6 apps, con tamaño propio, nombres opcionales y fondo translúcido.
- Fondo: montañas, fondo de pantalla de Android, foto propia o color sólido, con oscurecido ajustable.
- Gestos configurables: deslizar arriba, abajo, izquierda y derecha, doble toque y mantener presionado.

**Cajón de apps** (desliza hacia arriba)
- Cuadrícula de 3 a 7 columnas o lista; tamaño de íconos y de nombres, espacio entre filas, opacidad y alto del cajón
  (pantalla completa o una tarjeta más pequeña abajo).
- Pestañas por categoría (Social, Juegos, Música, Video, Fotos, Noticias, Mapas, Productividad), como en POCO.
- Orden alfabético, por instalación reciente o por color del ícono.
- Búsqueda sin acentos, búsqueda en la web si no hay resultados y opción de abrir la app sola cuando queda una.
- Mantén presionada una app para agregarla a Inicio o al dock, cambiarle el nombre, ocultarla, ver su información
  o desinstalarla. Se cierra deslizando hacia abajo.

**Íconos**
- Colores originales, monocromáticos o silueta de un solo color; forma de fondo (ninguna, círculo, cuadrado suave o
  cuadrado), color de la forma y del ícono, tamaño y paquetes de íconos.

**Tema**
- Tinta (oscuro), papel (claro) o negro puro; color de énfasis (Salvia, Crema, Atardecer, Cielo, Cancha y más, o
  cualquier color) y fuente (fina, normal, monoespaciada, condensada o serif), por separado para el reloj y para los
  nombres de las apps.

**Barra de estado, Centro de actividades y pantalla de bloqueo propios**
- **Barra de estado:** fondo, colores, qué mostrar, posición de la hora, tamaño y qué pasa al tocarla o deslizarla.
- **Centro de actividades** (no usa el panel de Android): botones a elegir y en tu orden, columnas, filas, esquinas,
  colores, opacidad, barra de brillo y notificaciones agrupadas o en orden de llegada.
- **Pantalla de bloqueo:** tamaño y posición de la hora, fecha, evento, notificaciones y oscurecido.
- Con el **Tema para todo el teléfono** (servicio de accesibilidad opcional) se usan en todas las apps.

**Copia de seguridad:** exporta e importa todos tus ajustes, o restablece todo.

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
- En **Personalizar → Permisos**, activa el acceso a **Notificaciones** (puntos en las apps, lo que está sonando y Centro de actividades).
- Para el clima, permite la ubicación o escribe tu ciudad en **Personalizar → Clima**. Para el próximo evento, permite el calendario.
- Mantén presionado el inicio para abrir el menú rápido de personalización.

## Limitaciones de Android
- Android no deja que ninguna app (tampoco Nova) borre su barra de estado, su panel ni su bloqueo. Mosaico los **tapa** con los
  suyos mediante el servicio de accesibilidad; los de Android siguen existiendo debajo (por ejemplo, si deslizas desde el borde
  en una app a pantalla completa).
- Mosaico no muestra widgets de otras apps de Android.
- La rotación, el brillo y "horas de silencio" necesitan permisos especiales que la app te pide la primera vez.

Proyecto independiente.

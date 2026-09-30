# Prompt maestro — App "Warriors Box"

Copiá todo lo que está debajo de la línea y pegalo en Claude Code (o en otra IA de programación) dentro de este repositorio.
Imágenes de referencia: `docs/referencias/logo-warriors-box.jpg` y `docs/referencias/estilo-rutina-ejemplo.jpg`.

---

## ROL Y OBJETIVO

Actuá como desarrollador Android senior. Construí desde cero, en este repositorio, una app Android nativa llamada **Warriors Box** que se instala como **APK directo (sideload), sin Play Store**, para llevar el registro diario de rutinas de gimnasio de varios usuarios en un mismo celular. **Uso personal: no se publicará en Play Store.**

El dueño del proyecto **no es programador**. Por eso:
- Todo debe compilar y generar el APK **automáticamente con GitHub Actions**, sin que él instale nada en su computadora.
- Cada decisión técnica importante se documenta en `README.md` en español simple.
- Nada de pasos manuales ocultos: si algo requiere configuración (por ejemplo una clave de API), se explica paso a paso.

## STACK TÉCNICO (obligatorio)

- Lenguaje: **Kotlin**. UI: **Jetpack Compose + Material 3**.
- `minSdk 26` (Android 8.0), `targetSdk` y `compileSdk` = **la API estable más reciente disponible** (mínimo 35/Android 15; usar 36/Android 16 o superior si el Android Gradle Plugin lo soporta).
- Arquitectura MVVM: `ui/` (pantallas Compose), `viewmodel/`, `data/` (repositorios), `data/local/` (Room), `data/remote/` (red), `domain/` (reglas de recomendación).
- Persistencia local: **Room** (base de datos SQLite). Preferencias: **DataStore**.
- Imágenes: **Coil**. Red: **Retrofit + OkHttp + kotlinx.serialization**.
- Inyección de dependencias: **Hilt** (o manual si simplifica).
- Navegación: **Navigation Compose** con rutas tipadas.
- Gradle con **version catalog** (`libs.versions.toml`) y Kotlin DSL.

### Requisitos de Android moderno (verificar cada uno)
1. **Edge-to-edge** obligatorio (Android 15+): usar `enableEdgeToEdge()` y respetar `WindowInsets` en todas las pantallas.
2. **Photo Picker** del sistema (`PickVisualMedia`) para la foto de fondo y foto de perfil: **no pedir permisos de almacenamiento**. Copiar la imagen elegida al almacenamiento interno de la app (no guardar solo el URI, porque caduca).
3. **Predictive back** habilitado (`android:enableOnBackInvokedCallback="true"`).
4. Soporte de **páginas de memoria de 16 KB** (no incluir librerías nativas `.so` sin alinear).
5. **Tema claro/oscuro** y tamaños de letra grandes (accesibilidad) sin romper el diseño.
6. Permiso `INTERNET` y `ACCESS_NETWORK_STATE` únicamente. La app debe **funcionar 100 % sin internet**; la conexión solo agrega funciones extra.
7. Firmar el APK de release con un keystore propio (ver sección CI). Nunca subir el keystore ni contraseñas al repositorio.

## IDENTIDAD VISUAL

- Logo: `docs/referencias/logo-warriors-box.jpg` (hexágono/cubo blanco con "WB", texto "WARRIORS BOX" en tipografía estilo stencil, fondo negro con humo).
  - Recrear el logo como **vector** (`VectorDrawable`) para ícono adaptativo del launcher (foreground blanco, background negro) y para el splash screen (`core-splashscreen`).
- Paleta base: negro `#0D0D0D`, gris carbón `#1E1E1E`, blanco `#FFFFFF`, acento dorado cálido `#E0A95B` (el destello bajo el logo). Tema oscuro por defecto.
- Tipografía: una fuente condensada/stencil para títulos (por ejemplo "Black Ops One" o "Saira Stencil One", licencia OFL, incluida en `res/font`) y una sans legible (Inter o Roboto) para el cuerpo.
- Tarjetas de ejercicio inspiradas en `docs/referencias/estilo-rutina-ejemplo.jpg`: número en círculo de color, nombre del ejercicio en negrita, series×repeticiones debajo, ilustración, y un recuadro inferior con la indicación técnica breve ("Sostén la pesa frente al pecho", etc.).
- **Fondo personalizable**: el usuario puede elegir una foto propia como fondo de la app (global) y opcionalmente una por usuario. Aplicar un velo oscuro ajustable (0–80 %) para que el texto siempre sea legible. Botón "Restaurar fondo original".

## PANTALLAS

### 1. Inicio
- Logo grande centrado sobre el fondo (foto personalizada o la predeterminada).
- **Solo dos botones grandes**: **USUARIOS** y **RUTINAS**.
- Un ícono pequeño de engranaje en la esquina para Ajustes (fondo, copia de seguridad, unidades). No agregar más botones.

### 2. Usuarios
- Lista de usuarios (tarjeta con foto, nombre, edad, objetivo). Botón flotante "+" para agregar. Tocar = ver/editar. Mantener presionado = eliminar (con confirmación que explique que se borrará también todo su historial).
- Formulario en pasos (wizard) con validación y barra de progreso:
  1. **Datos básicos**: nombre (obligatorio), foto opcional, fecha de nacimiento (calcular edad automáticamente), sexo (masculino/femenino/prefiero no decir — se usa solo para cálculos).
  2. **Medidas**: peso, altura (calcular y mostrar IMC con aclaración de sus límites), % de grasa opcional, cintura opcional. Unidades kg/lb y cm/ft configurables.
  3. **Experiencia**: nivel (principiante <6 meses / intermedio 6–24 meses / avanzado >2 años), días disponibles por semana (3–6), minutos por sesión (30/45/60/90), lugar (gimnasio completo / casa con mancuernas / casa sin equipo) y equipo disponible (checklist: mancuernas con su peso, barra, máquinas, bandas, banco, bicicleta).
  4. **Objetivo**: perder grasa / ganar músculo / fuerza / resistencia / salud general / rehabilitación ligera.
  5. **Salud (PAR-Q simplificado)**: historial de lesiones (lista con zona del cuerpo: hombro, rodilla, espalda baja, muñeca, cuello, tobillo, cadera, otra + texto libre + fecha aproximada + si sigue doliendo), cirugías, condiciones (hipertensión, diabetes, asma, problemas cardíacos, embarazo), medicamentos, si un médico le restringió ejercicio. Si responde "sí" a algo cardíaco o restricción médica: mostrar aviso claro de consultar a un profesional antes de entrenar.
  6. **Hábitos**: horas de sueño, nivel de estrés (1–5), actividad diaria (sedentario/activo), contacto de emergencia opcional.
- Historial de peso y medidas con gráfico simple de evolución.

### 3. Rutinas
1. Al entrar, **primero pregunta "¿Quién entrena hoy?"** mostrando las tarjetas de usuarios.
2. Luego muestra el **plan de 5 semanas × 6 días** (Lunes a Sábado; Domingo = descanso):
   - Selector superior de semana (Semana 1 … Semana 5) con pestañas deslizables.
   - Debajo, los 6 días como tarjetas: nombre del día, grupo muscular (ej. "Pierna", "Empuje", "Tracción"), cantidad de ejercicios y estado (pendiente / en curso / completado ✓).
   - Indicador de progreso general del plan (ej. 14/30 sesiones).
3. Al tocar un día: lista de ejercicios en tarjetas estilo imagen de referencia. Cada ejercicio muestra: series × repeticiones objetivo, peso sugerido, descanso, nota técnica, y **lo que hizo la semana anterior** en ese mismo ejercicio.
4. **Registro de la sesión** (lo más importante, debe ser rápido de usar con una mano):
   - Por cada serie: campos de peso y repeticiones precargados con el objetivo; botón ✓ para marcarla hecha.
   - RPE o "repeticiones en reserva" opcional (0–4).
   - **Temporizador de descanso** automático al marcar una serie, con vibración y notificación al terminar (pedir permiso `POST_NOTIFICATIONS` solo en ese momento).
   - Nota libre por ejercicio y por sesión; marcar dolor/molestia en una zona.
   - Guardado automático en cada cambio (si se cierra la app no se pierde nada).
5. **Editor del plan**: agregar, quitar, reordenar (arrastrar) y reemplazar ejercicios desde la biblioteca; copiar un día a otras semanas; duplicar una semana.
6. **Progresión automática** entre semanas (editable):
   - Semanas 1–4: si completó todas las series con las repeticiones máximas y RIR ≥ 2, subir peso (~2.5 % en tren superior, ~5 % en tren inferior, redondeado al incremento disponible); si no, subir 1 repetición.
   - **Semana 5 = descarga (deload)**: 60 % del volumen, mismo peso o 10 % menos.
   - Al terminar la semana 5, ofrecer "Generar nuevo ciclo de 5 semanas" partiendo de los últimos pesos.

### 4. Biblioteca de ejercicios
- Precargada (seed en Room) con **todos los ejercicios de las fuentes públicas** (wger + free-exercise-db, varios cientos), priorizando y traduciendo al español los ~150 más usados y efectivos. Clasificados por grupo muscular, patrón de movimiento, equipo, nivel y **zonas de lesión que lo contraindican**. Debe incluir como mínimo:
  - De la imagen de referencia: Sentadilla goblet, Remo a una mano, Press de pecho en el suelo, Peso muerto rumano, Press de hombro a una mano, Curl de bíceps, Puente de glúteos, Plancha elevada.
  - Gimnasio: sentadilla con barra, prensa de piernas, zancadas, extensión y curl de cuádriceps/femoral, hip thrust, press banca plano/inclinado, aperturas, fondos, jalón al pecho, dominadas (asistidas), remo con barra, remo en polea, face pull, press militar, elevaciones laterales, curl martillo, extensión de tríceps en polea, press francés, pantorrillas, crunch, plancha, pallof press, farmer walk, etc.
- Cada ejercicio: nombre en español, músculos, equipo, instrucciones en 3 pasos cortos, errores comunes, y ilustración (vector propio o imagen de la API; nunca imágenes con copyright sin licencia).
- Buscador y filtros. El usuario puede crear ejercicios propios.

### 5. Historial y progreso
- Calendario con los días entrenados.
- Por ejercicio: gráfico de peso máximo y volumen (series × reps × peso) en el tiempo; récords personales con celebración visual.
- Resumen semanal: sesiones completadas, volumen total, racha de días.

### 6. Ajustes
- Foto de fondo (elegir / quitar / intensidad del velo).
- Unidades (kg/lb, cm/ft).
- **Copia de seguridad**: exportar todo a un archivo `.json` (o `.zip` con fotos) usando el selector de archivos del sistema, e importar desde ese archivo. Recordatorio mensual de hacer copia.
- Recordatorios de entrenamiento (ver sección Recordatorios).
- Modo entrenador activado/desactivado (ver sección Modo entrenador).
- Acerca de / versión.

## CONEXIÓN A INTERNET Y RECOMENDACIONES REALISTAS

Implementar en dos capas:

**Capa 1 — Motor local basado en reglas (funciona sin internet, obligatorio):**
- Genera el plan de 5 semanas según: nivel, días disponibles, minutos por sesión, objetivo, equipo y lesiones.
- Distribuciones por días: 3 días = cuerpo completo; 4 = torso/pierna ×2; 5 = torso/pierna/empuje/tracción/pierna; 6 = empuje/tracción/pierna ×2.
- Volumen por grupo muscular por semana: principiante 8–10 series, intermedio 10–16, avanzado 14–20.
- Rangos: fuerza 3–6 reps; hipertrofia 6–12; resistencia/pérdida de grasa 12–15 + cardio.
- **Excluir o sustituir** ejercicios contraindicados por lesión activa (ej. dolor de hombro → evitar press militar con barra, sugerir landmine press; espalda baja → evitar peso muerto convencional, sugerir puente de glúteos / hip thrust).
- Peso inicial sugerido conservador según nivel y peso corporal, con aviso de "ajustá a una carga que te deje 2–3 repeticiones en reserva".
- Cada recomendación muestra **por qué** se eligió ("Elegido porque tenés 4 días y objetivo hipertrofia").

**Capa 2 — Base de conocimiento desde internet (fuentes públicas, SIN inteligencia artificial):**
- **No usar IA ni claves de API de pago.** Todo el conocimiento viene de fuentes públicas con licencia abierta:
  - **wger** (`https://wger.de/api/v2/`, CC-BY-SA): ejercicios, músculos, equipo, imágenes y variaciones.
  - **free-exercise-db** (`https://github.com/yuhonas/free-exercise-db`, dominio público): 800+ ejercicios con instrucciones e imágenes.
  - **Compendium of Physical Activities** (valores MET públicos) para el cálculo de calorías.
- Estas fuentes se descargan **en tiempo de compilación** para armar la biblioteca inicial (así la app trae todo desde el primer uso, sin internet), y la app **sincroniza novedades** una vez por semana con WorkManager cuando hay Wi-Fi. Cachear en Room.
- Traducir al español los nombres e instrucciones de los ejercicios más comunes (tabla de traducción propia en el código); si falta traducción, mostrar el original.
- Mostrar el crédito/licencia de cada fuente en "Acerca de". **No** copiar contenido de sitios con copyright ni hacer scraping de páginas web.
- Manejar sin conexión, timeout y errores con mensajes claros; nunca bloquear la app.

## ALTERNATIVAS: "NO PUEDO HACER ESTE EJERCICIO"

- En cada ejercicio del plan y durante la sesión, botón **"No puedo hacerlo"** que pregunta el motivo: *no tengo el equipo / máquina ocupada / me duele o molesta / no sé hacerlo / muy difícil*.
- Según el motivo, ofrecer **3–5 alternativas que trabajen el mismo músculo principal y patrón de movimiento** (empuje horizontal, empuje vertical, tracción horizontal, tracción vertical, sentadilla, bisagra de cadera, zancada, core, aislamiento), filtradas por el equipo disponible y las lesiones del usuario, ordenadas de la más parecida a la menos. Cada una con su dificultad y el motivo de la sugerencia.
  - "Me duele" → solo variantes de menor carga articular (ej. press banca → flexiones inclinadas / press con mancuernas agarre neutro) y registrar la molestia en el perfil.
  - "Muy difícil" → regresión (ej. dominadas → dominadas asistidas / jalón / remo invertido).
- El usuario elige si el cambio es **solo por hoy** o **para todo el plan**. El historial de progreso se conecta al ejercicio realmente hecho.
- Cada ejercicio de la biblioteca guarda su patrón de movimiento, músculos primarios/secundarios, equipo y nivel, para que las alternativas se calculen automáticamente.

## RECORDATORIOS

- Por usuario: elegir días y hora de entrenamiento; notificación ("Hoy toca: Pierna — Semana 2 Día 3") con `AlarmManager`/WorkManager, que sobreviva al reinicio del teléfono (`RECEIVE_BOOT_COMPLETED`).
- Aviso opcional si pasaron 2 días sin entrenar en un día programado, y recordatorio mensual de copia de seguridad.
- Pedir permiso de notificaciones (`POST_NOTIFICATIONS`) al activar el primer recordatorio, explicando para qué. Si se necesita alarma exacta, pedir `SCHEDULE_EXACT_ALARM` con explicación o usar alarma inexacta como alternativa.
- Tocar la notificación abre directamente la sesión de ese usuario y día.

## MODO ENTRENADOR

- Se activa en Ajustes y se protege con un **PIN de 4 dígitos**.
- El entrenador ve un **panel con todos los alumnos**: última sesión, sesiones de la semana, cumplimiento del plan (%), récords recientes y alertas (alumno que no entrena hace 5+ días, molestias registradas).
- Puede **crear y editar planes** para cada alumno, copiar un plan de un alumno a otro, y dejar notas/indicaciones por ejercicio que el alumno ve en su sesión.
- Los alumnos pueden registrar sus sesiones sin PIN, pero **no** pueden editar su plan ni ver los datos de otros si el modo entrenador está activo.
- Exportar/importar el plan de un alumno como archivo para pasarlo a otro celular (por WhatsApp, etc.).

## COMPARTIR EN INSTAGRAM (imagen de resumen)

- Al terminar una sesión, botón **"Compartir"** que genera una **imagen PNG** con el estilo Warriors Box (logo, fondo negro/foto del usuario con velo, acento dorado), en dos formatos: **historia 1080×1920** y **post 1080×1350**.
- Contenido: nombre (opcional), fecha, rutina del día, duración, ejercicios hechos con series × reps × peso, volumen total, récords personales del día, racha de días y **calorías quemadas estimadas**.
- **Calorías**: `kcal = MET × peso_kg × horas` usando MET del Compendium (pesas moderado ≈ 3.5, vigoroso ≈ 6.0, bici suave ≈ 4.0 para el calentamiento), según duración real y RPE. Mostrar "≈" y aclarar que es una estimación.
- Compartir con el menú del sistema (`ACTION_SEND` + `FileProvider`) y opción de guardar en la galería (MediaStore, sin permisos). También resumen semanal compartible.

## CALIDAD Y PRUEBAS (obligatorio correrlas y reportar resultados)

1. **Pruebas unitarias** (JUnit + Truth/MockK): motor de recomendaciones (todas las combinaciones de días/nivel/objetivo; exclusión por lesión), alternativas por motivo, progresión semanal y deload, cálculo de edad/IMC/conversión de unidades, cálculo de calorías, generación de la imagen para compartir, PIN del modo entrenador, programación de recordatorios, exportar→importar produce los mismos datos.
2. **Pruebas de base de datos** (Room in-memory): CRUD de usuarios, borrado en cascada del historial, migraciones.
3. **Pruebas de UI** (Compose UI Test / Robolectric): flujo completo crear usuario → elegir usuario → abrir Semana 1 Día 1 → registrar series → ver completado.
4. **Lint** de Android sin errores y **detekt/ktlint** para estilo.
5. Probar en emuladores de **API 26, 30, 34 y la más reciente** (matriz en CI si es viable; como mínimo la más reciente).
6. Después de correr todo, escribir `docs/INFORME_PRUEBAS.md` en español: qué se probó, qué pasó, qué falló y cómo se corrigió, y limitaciones conocidas.

## COMPILACIÓN Y DISTRIBUCIÓN (GitHub Actions)

- Workflow `.github/workflows/build.yml` que en cada push: ejecuta lint + pruebas, compila `assembleRelease` y publica el **APK firmado** como artefacto descargable; en cada tag `v*` crea un **Release** de GitHub con el APK adjunto.
- Firma: el keystore se guarda como secreto de GitHub en base64 (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Documentar en el README cómo generarlo **una sola vez** y advertir que si se pierde, las actualizaciones futuras no se podrán instalar encima de la app existente (habría que desinstalar y se perderían los datos no respaldados).
- `versionCode` incremental automático para que cada APK nuevo se instale como actualización sin borrar datos.
- README con: cómo descargar el APK desde GitHub en el celular, cómo habilitar "Instalar apps desconocidas", qué significa la advertencia de Play Protect, y cómo actualizar sin perder datos.

## ACTUALIZACIONES DENTRO DE LA APP (alerta de nueva versión)

Una vez instalado el primer APK, las siguientes versiones deben llegar desde la propia app, sin Play Store:

1. **Fuente de verdad**: los **Releases de GitHub** de este repositorio. Cada release tiene tag `vX.Y.Z` y el APK adjunto como asset. Opcionalmente un archivo `update.json` en el release con `versionCode`, `versionName`, `apkUrl`, `sha256`, `notas` (en español) y `obligatoria` (true/false).
2. **Comprobación**: al abrir la app (máximo una vez cada 12 h) y con un botón "Buscar actualizaciones" en Ajustes, consultar `https://api.github.com/repos/<owner>/<repo>/releases/latest`. Comparar con el `versionCode` instalado. Hacerlo en segundo plano con **WorkManager** (también una revisión diaria con red disponible).
3. **Alerta**: si hay versión nueva, mostrar un diálogo con: versión actual → nueva, notas de la versión ("Qué hay de nuevo"), y botones **Actualizar ahora** / **Más tarde** / **Omitir esta versión**. Además, una notificación del sistema si la app está cerrada. Si `obligatoria = true`, no ofrecer "Omitir".
4. **Descarga e instalación**: descargar el APK con barra de progreso (`DownloadManager` o OkHttp a la caché de la app), **verificar el SHA-256** y que la firma del APK coincida con la de la app instalada; luego lanzar el instalador del sistema con **`PackageInstaller`** (o `ACTION_VIEW` + `FileProvider`). Declarar `REQUEST_INSTALL_PACKAGES` y, si el permiso "Instalar apps desconocidas" no está concedido para Warriors Box, llevar al usuario a esa pantalla de ajustes con una explicación simple.
5. **Datos**: la actualización se instala **encima** de la app y conserva todos los datos. Antes de instalar, hacer automáticamente una copia de seguridad local (export JSON).
6. **Migraciones de Room**: cada cambio de esquema de base de datos debe tener su migración escrita y probada; **prohibido** `fallbackToDestructiveMigration` (borraría los datos del usuario al actualizar).
7. **Publicar una versión nueva** debe ser un solo paso para el dueño: crear un tag `vX.Y.Z` (o ejecutar el workflow manualmente con un botón en GitHub Actions indicando la versión y las notas). El workflow sube `versionCode`, compila, firma con el **mismo keystore**, calcula el SHA-256, genera `update.json` y publica el Release.
8. El repositorio es **público** (`AmargoRM/Warriors-Box`), así que la app consulta sus releases directamente sin token.
9. Pruebas: unitarias para la comparación de versiones y el parseo de `update.json`; prueba manual documentada instalando v1.0.0, publicando v1.0.1 y verificando alerta, descarga, instalación y que los datos siguen ahí.

## REGLAS DE TRABAJO

- Avanzá por fases y hacé commit al final de cada una: (1) esqueleto + tema + CI que genera APK vacío; (2) Room + Usuarios; (3) Biblioteca + seed; (4) Rutinas + registro; (5) motor de recomendaciones; (6) alternativas "No puedo hacerlo"; (7) sincronización con fuentes públicas; (8) historial/gráficos + imagen para Instagram; (9) recordatorios; (10) modo entrenador; (11) backup; (12) actualizaciones dentro de la app; (13) pruebas finales + informe.
- No dejes TODOs ni pantallas "próximamente". Si algo no se puede hacer, explicalo en el README.
- Textos de la app en español neutro, centralizados en `strings.xml`.
- Incluir un aviso visible (una vez, al crear el primer usuario): "Esta app no reemplaza la opinión de un médico o entrenador certificado."

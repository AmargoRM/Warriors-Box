# Prompt maestro — App "Warriors Box"

Copiá todo lo que está debajo de la línea y pegalo en Claude Code (o en otra IA de programación) dentro de este repositorio.
Imágenes de referencia: `docs/referencias/logo-warriors-box.jpg` y `docs/referencias/estilo-rutina-ejemplo.jpg`.

---

## ROL Y OBJETIVO

Actuá como desarrollador Android senior. Construí desde cero, en este repositorio, una app Android nativa llamada **Warriors Box** que se instala como **APK directo (sideload), sin Play Store**, para llevar el registro diario de rutinas de gimnasio de varios usuarios en un mismo celular.

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
- Precargada (seed en Room) con al menos 60 ejercicios clasificados por grupo muscular, equipo, nivel y **zonas de lesión que lo contraindican**. Debe incluir como mínimo:
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
- Clave de API (opcional) para recomendaciones con IA — guardada cifrada, nunca en el código.
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

**Capa 2 — Con internet (opcional, mejora la capa 1):**
- Sincronizar la biblioteca de ejercicios con la **API pública de wger** (`https://wger.de/api/v2/`, gratuita y de código abierto) para traer más ejercicios e imágenes con licencia libre. Cachear en Room.
- **Recomendaciones con IA** (solo si el usuario configuró una clave de API): enviar el perfil **anonimizado** (sin nombre, sin foto, sin contacto) y el historial resumido; pedir la respuesta en JSON con esquema fijo; **validar** la respuesta contra la biblioteca local y las reglas de seguridad antes de mostrarla (rechazar ejercicios contraindicados o volúmenes fuera de rango). El usuario siempre ve la propuesta y decide si aplicarla.
- Manejar sin conexión, timeout y errores con mensajes claros; nunca bloquear la app.

## CALIDAD Y PRUEBAS (obligatorio correrlas y reportar resultados)

1. **Pruebas unitarias** (JUnit + Truth/MockK): motor de recomendaciones (todas las combinaciones de días/nivel/objetivo; exclusión por lesión), progresión semanal y deload, cálculo de edad/IMC/conversión de unidades, validación de respuestas de la IA, exportar→importar produce los mismos datos.
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
8. Si el repositorio es **privado**, la API de GitHub no deja descargar sin token: en ese caso publicar los releases en un repositorio público separado solo para APKs (ej. `warriors-box-releases`) y documentarlo.
9. Pruebas: unitarias para la comparación de versiones y el parseo de `update.json`; prueba manual documentada instalando v1.0.0, publicando v1.0.1 y verificando alerta, descarga, instalación y que los datos siguen ahí.

## REGLAS DE TRABAJO

- Avanzá por fases y hacé commit al final de cada una: (1) esqueleto + tema + CI que genera APK vacío; (2) Room + Usuarios; (3) Biblioteca + seed; (4) Rutinas + registro; (5) motor de recomendaciones; (6) internet (wger + IA opcional); (7) historial/gráficos; (8) backup; (9) actualizaciones dentro de la app; (10) pruebas finales + informe.
- No dejes TODOs ni pantallas "próximamente". Si algo no se puede hacer, explicalo en el README.
- Textos de la app en español neutro, centralizados en `strings.xml`.
- Incluir un aviso visible (una vez, al crear el primer usuario): "Esta app no reemplaza la opinión de un médico o entrenador certificado."

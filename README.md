# Warriors Box

App de Android para llevar el registro diario de rutinas de gimnasio de varias personas en un mismo celular.
Se instala como **APK** (sin Play Store) y se **actualiza sola** desde los Releases de este repositorio.

![Logo](docs/referencias/logo-warriors-box.jpg)

## Qué hace

- **Inicio** con solo dos botones: **Usuarios** y **Rutinas**. El engranaje abre los Ajustes.
- **Usuarios**: perfil en 6 pasos:
  1. Datos básicos.
  2. Medidas, con IMC.
  3. Experiencia y equipo.
  4. Objetivo.
  5. Salud: lesiones, condiciones y restricción médica.
  6. Hábitos.

  Además tiene gráfico de peso, foto de perfil y fondo propio para cada usuario.
- **Rutinas**: primero pregunta **"¿Quién entrena hoy?"**. Luego muestra un plan de **5 semanas × 6 días** (lunes a sábado):
  - El plan se genera según el perfil, sin internet.
  - La semana 5 es de **descarga** (más liviana).
  - La **progresión es automática**: si completaste las series, la semana siguiente sube el peso o las repeticiones.
- **Registro de la sesión**:
  - Peso, repeticiones y RIR (repeticiones en reserva) por serie.
  - **Temporizador de descanso** con vibración y aviso.
  - Notas.
  - "Lo que hiciste la última vez".
  - Guardado automático.
- **"No puedo hacerlo"**: pide el motivo (no tengo el equipo, máquina ocupada, me duele, no sé hacerlo, muy difícil) y ofrece de 3 a 5 alternativas para el **mismo músculo**. El cambio puede ser solo por hoy o para todo el plan.
- **Frases irónicas y satíricas** al terminar cada ejercicio, adaptadas al tipo de ejercicio. También hay frases especiales para los récords personales y para el final de la sesión.
- **Imagen para Instagram** en formato historia (1080×1920) o post (1080×1350). Incluye ejercicios, volumen, récords, racha y **calorías estimadas** (fórmula MET).
- **Recordatorios** por usuario (días y hora):
  - Muestran lo que toca, por ejemplo "Hoy toca: Pierna — Semana 2".
  - Avisan si pasan 2 días programados sin entrenar.
- **Modo entrenador**:
  - Un PIN protege la edición de planes y perfiles.
  - Tiene un panel de alumnos con cumplimiento, récords y alertas.
  - El entrenador deja notas en cada ejercicio.
  - Los planes se pueden exportar e importar en un archivo.
- **Coach a distancia** (dos celulares con la app):
  1. El alumno abre su perfil → "Vincular con mi coach" y muestra un **QR**. Si no están juntos, puede compartir el código por WhatsApp.
  2. El coach va a Usuarios → "Soy coach: vincular alumno" y lo escanea. El alumno aparece en su celular con su perfil completo.
  3. El coach arma el plan (ejercicios, series, repeticiones, peso y notas) y toca **"Enviar plan"**.
  4. Al alumno le llega **dentro de la app**. Al abrirla ve "Plan nuevo de tu coach" con las opciones Aceptar, Rechazar o Más tarde.
  5. Si el alumno cambia el plan, primero ve una advertencia. Si confirma, al coach le llega un aviso con el detalle de los cambios.
  - Los mensajes viajan **cifrados** por el buzón público y gratuito [ntfy.sh](https://ntfy.sh), sin cuentas. La llave solo está en el QR.
  - Si el alumno no abre la app a tiempo, el celular del coach reenvía el plan solo cada 2 horas y media, hasta 14 días.
- **Ejercicios propios con fotos**: si un ejercicio no existe, se crea a mano con nombre, músculos, equipo, instrucciones, consejo y hasta 3 fotos (galería o cámara). Si el coach lo usa en un plan, viaja con sus fotos al celular del alumno.
- **Eliminar perfil**: con el basurero de cada usuario en la lista, o desde su perfil. Siempre pide confirmación.
- **Biblioteca de 889 ejercicios**:
  - 150 están curados en español, con técnica en 3 pasos.
  - Tiene buscador y filtros, y puedes crear ejercicios propios.
  - Se sincroniza cada semana con fuentes públicas (free-exercise-db y wger) **sin inteligencia artificial**.
- **Historial**: calendario, resumen semanal, gráficos por ejercicio (peso, 1RM estimado, volumen) y récords.
- **Fondo personalizado** con velo oscuro ajustable.
- **Copias de seguridad**: un archivo .zip con los datos y las fotos, más una copia automática antes de cada actualización.
- **Actualizaciones dentro de la app**:
  - Aviso "Nueva versión disponible" con las novedades.
  - Opciones: Actualizar ahora, Más tarde u Omitir.
  - Antes de instalar, verifica el SHA-256 y la firma del APK.

## Instalar en el celular (primera vez)

1. En el celular, abre `https://github.com/AmargoRM/Warriors-Box/releases/latest`.
2. Descarga el archivo `warriors-box-vX.Y.Z.apk`.
3. Ábrelo. Android pedirá permitir **"Instalar apps desconocidas"** para el navegador o para el administrador de archivos: actívalo.
4. Si aparece **Play Protect** ("app no reconocida"), toca **Más detalles → Instalar de todas formas**.
   Es normal en apps que no vienen de Play Store.

> Instala la versión **publicada (Release)**, no el APK de prueba ("debug") que dejan las compilaciones.
> Solo la versión publicada recibe actualizaciones.

## Actualizar

- La app revisa sola, una vez al día, si hay una versión nueva. Cuando la hay, avisa con una notificación y con un mensaje al abrirla.
- Al tocar **Actualizar ahora**:
  1. Descarga la versión nueva.
  2. Verifica que no esté dañada y que sea tuya (misma firma).
  3. Hace una copia de seguridad automática.
  4. Abre el instalador de Android.
- La actualización se instala **encima** y **conserva todos los datos**.
- La primera vez, Android pedirá permitir que Warriors Box "instale apps desconocidas".

## Publicar una versión nueva (para el dueño del repositorio)

Una sola vez: cargar la llave de firma en GitHub, siguiendo [docs/LLAVE_DE_FIRMA.md](docs/LLAVE_DE_FIRMA.md).

Cada vez que quieras publicar:

1. Abre la pestaña **Actions** del repositorio.
2. Elige **Publicar versión** y toca **Run workflow**.
3. Escribe la versión (por ejemplo `1.0.1`, siempre mayor que la anterior) y las notas.
4. Toca **Run**.

En unos 10 minutos aparece el Release con el APK y `update.json`, y los celulares reciben el aviso.

## Cómo está hecha (resumen técnico)

| Parte | Tecnología |
|---|---|
| Lenguaje / UI | Kotlin + Jetpack Compose + Material 3 |
| Android | mínimo 8.0 (API 26), compilada para Android 16 (API 36) |
| Datos | Room (SQLite) + DataStore, 100 % local |
| Imágenes | Coil (fotos locales y remotas con caché) |
| Red | OkHttp (solo sincronizar el catálogo y buscar actualizaciones) |
| Tareas en segundo plano | WorkManager + AlarmManager |
| Lógica | módulo `:core` en Kotlin puro (motor de rutinas, progresión, alternativas, calorías, frases), probado sin Android |
| Compilación | GitHub Actions (no hace falta instalar nada en tu computadora) |

Requisitos de Android moderno que cumple:

- Pantalla de borde a borde (obligatoria desde Android 15).
- **Selector de fotos del sistema**, sin pedir permisos de almacenamiento.
- Gesto de "atrás" predictivo.
- Ícono adaptativo y temático.
- Pantalla de inicio (splash) moderna.
- Permiso de notificaciones pedido solo cuando hace falta.

### Estructura

```
core/     Lógica pura y sus pruebas (sin Android)
app/      App Android (pantallas, base de datos, notificaciones, actualizaciones)
tools/    Generador del catálogo de ejercicios (Python)
docs/     Guías e informe de pruebas
.github/  Compilación, pruebas, emuladores y publicación automática
```

### Decisiones y límites conocidos

- **Sin inteligencia artificial**: las recomendaciones salen de reglas fijas (volumen por nivel, rangos por objetivo, exclusión por lesión). Son predecibles y funcionan sin internet.
- Los textos de la app están escritos directamente en el código de las pantallas, no en `strings.xml`. La app es solo en español y así el código es más fácil de leer.
- En el editor del plan, los ejercicios se reordenan con flechas ↑↓ en lugar de arrastrarlos. Es más preciso con el dedo y más fácil de mantener.
- El temporizador de descanso avisa con una alarma "no exacta" cuando la app está en segundo plano. Así no hace falta pedir el permiso especial de alarmas exactas, pero el aviso puede llegar algunos segundos tarde.
- Los ejercicios que vienen de internet se muestran con su nombre original (en inglés) cuando no tienen traducción.
- **Coach a distancia sin servidor propio**: usa ntfy.sh como buzón.
  - ntfy.sh guarda los mensajes 12 horas y los adjuntos 3 horas. Por eso el coach reenvía el plan hasta que el alumno confirma que le llegó.
  - Si ntfy.sh dejara de funcionar, el envío dentro de la app fallaría, pero no se pierde nada: queda el menú ⋮ → Exportar plan (archivo).
  - El estado del modo coach se guarda en un archivo propio, no en la base de datos, así que esta función no necesitó migrar la base.
- Las calorías son una **estimación** con valores MET (Compendium of Physical Activities), no una medición.
- **Esta app no reemplaza la opinión de un médico o entrenador certificado.**

### Créditos y licencias

- Buzón del modo coach: [ntfy.sh](https://ntfy.sh) (servicio público, Apache 2.0 / GPLv2). QR: [ZXing](https://github.com/zxing/zxing) y zxing-android-embedded (Apache 2.0).
- Ejercicios: [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (Unlicense, dominio público) y [wger](https://wger.de) (CC-BY-SA 4.0).
- Fuente: Black Ops One (SIL Open Font License 1.1).
- Valores MET: Compendium of Physical Activities.

Informe de pruebas: [docs/INFORME_PRUEBAS.md](docs/INFORME_PRUEBAS.md).

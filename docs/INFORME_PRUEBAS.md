# Informe de pruebas — Warriors Box

Fecha: 30 de septiembre de 2026.
Dónde se corrieron: GitHub Actions (servidores de GitHub), en el flujo **"Compilar y probar"**, que corre automáticamente en cada cambio.

## Resumen

| Grupo | Pruebas | Resultado |
|---|---|---|
| Lógica central (`:core`) | 27 | ✅ todas pasan |
| App Android (`:app`, Robolectric) | 13 | ✅ todas pasan |
| Análisis de código (Android lint) | — | ✅ sin errores |
| Compilación del APK | — | ✅ |
| Emuladores (Android 8.0, 11, 14 y 15) | 4 | ver sección "Emuladores" |

## Qué se probó

### Lógica central (sin Android)

- **Motor de rutinas**: probado con las 360 combinaciones posibles de perfil:
  - 3 niveles × 6 objetivos × 4 opciones de días × 3 lugares × 4 duraciones.

  En todas se verificó que:
  - El plan tiene 5 semanas × 6 días.
  - Los días obligatorios son los que eligió el usuario.
  - Ningún día queda vacío y ningún ejercicio se repite en el mismo día.
  - Solo se usa el equipo disponible.
  - La semana 5 es de descarga.
- **Lesiones**: con molestias en hombro, espalda baja y rodilla, no aparece ningún ejercicio contraindicado.
- **Principiantes**: nunca reciben ejercicios de nivel intermedio o avanzado, y solo ejercicios curados en español.
- **Casa sin equipo**: solo ejercicios con el peso del cuerpo.
- **Progresión**:
  - Sube el peso si completas todo con reserva: 5 % en pierna y 2,5 % en torso, redondeado a discos reales.
  - Si completas el objetivo, pero no el máximo de repeticiones, sube 1 repetición.
  - Si fallas, repite la carga.
  - En la semana de descarga baja series y peso.
- **"No puedo hacerlo"**:
  - Las alternativas trabajan el mismo músculo.
  - Respetan el motivo (sin barra, dolor, muy difícil) y el equipo de la casa.
- **Calorías**: fórmula MET verificada con números conocidos.
- **Frases irónicas**: se elige el tipo correcto (pierna, empuje, core, récord…) y no se repite la última.
- **Actualizaciones**: cálculo del número de versión, lectura de `update.json`, "omitir versión" y "obligatoria".
- **Otros cálculos**:
  - Recordatorios (próximo aviso).
  - PIN del entrenador (se guarda cifrado, nunca el número).
  - Récords personales y rachas.
  - Conversión kg/lb y cm/pies.
  - Edad e IMC.
- **Fuentes de internet**: lectura de free-exercise-db y de wger, con muestras reales de su formato.

### App Android (Robolectric: Android simulado dentro de la computadora)

- El catálogo de 889 ejercicios se carga desde la app.
- Se genera el plan y se guardan las 30 jornadas; las lesiones se respetan.
- **Sesión completa**:
  1. Se empieza la sesión; al retomarla es la misma, no se duplica.
  2. Se marcan las series.
  3. Se termina la sesión.

  Resultado: se calculan las calorías y la semana 2 sube de peso.
- Los récords se detectan contra el historial.
- "No puedo hacerlo → para todo el plan" cambia el ejercicio en las semanas siguientes.
- **Editor del plan**: agregar, reordenar, copiar a otras semanas (incluida la de descarga) y quitar ejercicios.
- **Copia de seguridad**: se exporta, se borra todo y se importa; queda exactamente igual.
- Borrar un usuario borra en cascada su plan y sus sesiones.
- Un plan exportado e importado en otro usuario queda idéntico.
- El panel del entrenador genera alertas.
- **Flujo de pantallas completo**:
  1. Inicio.
  2. Rutinas → Crear usuario (los 6 pasos) → aviso de salud.
  3. Elegir usuario → Generar plan.
  4. Semana 1, lunes → Empezar → marcar una serie.
  5. Terminar → Resumen.

## Problemas encontrados y corregidos durante el desarrollo

1. **Pesos iniciales demasiado bajos** para personas de nivel intermedio: se recalibraron por tipo de ejercicio y músculo.
2. **Días vacíos** para quien entrena en casa sin equipo (no había ejercicios de espalda sin barra): se agregaron "Remo bajo una mesa", "Y-T-W boca abajo" y "Superman".
3. **Ejercicios sin traducir** en planes de principiantes: ahora la rotación solo alterna entre opciones de la misma calidad.
4. Formato de libras ("22.0 lb" → "22 lb").
5. Una función de la barra de semanas era "experimental" y no compilaba: se marcó correctamente.
6. **Prueba de pantallas**: tocaba "Siguiente" una vez menos de lo necesario y tocaba "Empezar" antes de que se activara. Eran errores de la prueba, no de la app, y se corrigieron.

## Emuladores

El flujo **"Probar en emuladores"** hace lo siguiente en Android 8.0 (API 26), 11 (API 30), 14 (API 34) y 15 (API 35):

1. Instala el APK.
2. Abre la app y toca "Usuarios" y "Rutinas".
3. Guarda capturas de pantalla.
4. Verifica que la app no se cierre sola.

Las capturas quedan como archivos descargables del run.

## Limitaciones conocidas (lo que las pruebas NO cubren)

- **Las pruebas no reemplazan a tu celular**:
  - Robolectric y los emuladores simulan Android.
  - La cámara, el selector de fotos, las notificaciones reales y la instalación de actualizaciones se deben probar a mano en tu teléfono.
- **La instalación de una actualización** (v1.0.0 → v1.0.1) solo se puede probar cuando existan dos versiones publicadas con la llave de firma cargada. Prueba manual sugerida:
  1. Instalar v1.0.0 y crear un usuario.
  2. Publicar v1.0.1.
  3. Abrir la app: debe aparecer "Nueva versión disponible".
  4. Tocar Actualizar y verificar que el usuario sigue ahí.
- **Sincronización con wger**: no se pudo probar contra el servidor real, porque el entorno de desarrollo no tenía acceso a wger.de. Se probó con una muestra de su formato. Si wger cambia su formato, la sincronización lo ignora sin romper la app.
- **Temporizador de descanso**: con la app en segundo plano, el aviso usa una alarma "no exacta" y puede llegar algunos segundos tarde.

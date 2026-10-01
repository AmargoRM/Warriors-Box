# La llave de firma (hacer UNA sola vez)

## Qué es y por qué importa

Android solo deja instalar una actualización **encima** de la app si está firmada con **la misma llave**
que la primera versión que instalaste. La llave es un archivo (`warriors-box-release.jks`) más una contraseña.

⚠️ **Si la llave se pierde:**

1. Ninguna versión nueva se podrá instalar encima de la app que tienes.
2. La única salida sería desinstalar la app e instalar la nueva desde cero.
3. Al desinstalar, Android **borra todos los datos**: usuarios, planes, sesiones, récords y fotos.
4. No hay forma de recuperar la llave: nadie tiene una copia (ni GitHub ni Google).

Por eso:

- La llave **nunca** se sube al repositorio (el repositorio es público).
- Se guarda como **secreto** de GitHub (solo lo puede leer el flujo que publica versiones).
- Guarda además una copia en un lugar seguro y privado (por ejemplo, tu Google Drive personal).
- Exporta seguido una copia de seguridad desde la app (**Ajustes → Copia de seguridad → Exportar**).

## Paso a paso para cargarla en GitHub

Necesitas los 4 valores que te entregó Claude (archivos `KEYSTORE_BASE64.txt`, `KEYSTORE_PASSWORD.txt`,
`KEY_ALIAS.txt` y `KEY_PASSWORD.txt`).

1. Abre `https://github.com/AmargoRM/Warriors-Box/settings/secrets/actions`
   (repositorio → **Settings** → **Secrets and variables** → **Actions**).
2. Toca **New repository secret** y crea estos 4 secretos, copiando y pegando el contenido de cada archivo
   **completo, sin espacios ni saltos de línea extra**:

   | Nombre del secreto  | Contenido                         |
   |---------------------|-----------------------------------|
   | `KEYSTORE_BASE64`   | todo el texto de `KEYSTORE_BASE64.txt` (es largo, ~5.700 caracteres) |
   | `KEYSTORE_PASSWORD` | el texto de `KEYSTORE_PASSWORD.txt` |
   | `KEY_ALIAS`         | `warriorsbox`                     |
   | `KEY_PASSWORD`      | el texto de `KEY_PASSWORD.txt`    |

3. Listo. A partir de ahí, cada versión publicada queda firmada con la misma llave.

Consejo: es más cómodo hacerlo desde una computadora que desde el celular, por lo largo del primer valor.

# Espacia — Backend

API REST para reservar salas de estudio, equipos y talleres de una biblioteca.
Spring Boot 4 · Java 25 · Spring Security + JWT · PostgreSQL.

El frontend está en el repositorio [Espacia-Frontend](https://github.com/Sebxx66/Espacia-Frontend).

## Vista previa

La interfaz completa está en el repositorio del frontend.

| Catálogo de espacios | Panel del administrador |
|---|---|
| ![Catálogo de espacios](docs/screenshots/02-catalogo.jpg) | ![Panel del administrador](docs/screenshots/05-admin-dashboard.jpg) |

## Funciones

- Registro e inicio de sesión con roles (estudiante, funcionario y administrador).
- Catálogo de espacios con ocupación en tiempo real y reservas sin choques de horario.
- Reglas de reserva configurables en código: horario, duración, reservas activas y límite diario.
- Correos automáticos: bienvenida, confirmación, cancelación o anulación, recordatorio antes de la reserva y recuperación de contraseña.
- Notificaciones dentro de la aplicación, incidencias de soporte técnico y panel de estadísticas.
- Protección contra abuso: bloqueo temporal tras varios intentos fallidos de inicio de sesión y límite de solicitudes de recuperación de contraseña.

## Requisitos

- Java 25
- Docker Desktop (para la base de datos), o un PostgreSQL propio

## Puesta en marcha

1. Levanta la base de datos (los datos se guardan en el volumen `bibliospace-data`):

   ```bash
   docker compose up -d
   ```

2. Arranca el backend en `http://localhost:8080`:

   ```bash
   ./mvnw spring-boot:run
   ```

   En Windows: `mvnw.cmd spring-boot:run`.

Al primer arranque se crean automáticamente:

- **Un administrador** (si no existe ninguno), con el correo y la contraseña de `ADMIN_EMAIL` y `ADMIN_PASSWORD`.
  Defínelos antes del primer arranque y cambia la contraseña desde la aplicación después de entrar.
- **Datos de demostración** (desactívalos con `SEED_DEMO_DATA=false`): seis espacios de ejemplo, dos estudiantes, un funcionario, reservas de las últimas semanas, incidencias y notificaciones.

## Correo electrónico

Sin configurar nada, la aplicación funciona igual y registra en el log los correos que habría enviado.
Para enviarlos de verdad define `MAIL_USERNAME` y `MAIL_PASSWORD`. Con Gmail usa una
[contraseña de aplicación](https://support.google.com/accounts/answer/185833), no la contraseña normal de la cuenta.
Para un uso real conviene un proveedor transaccional (Brevo, Resend, Amazon SES) con tu propio dominio.

## Copias de seguridad

Los scripts de `scripts/respaldo/` guardan toda la base en un archivo comprimido con la fecha en el nombre
(`espacia-2026-11-02_030000.dump`), comprueban que se puede leer, lo anotan en `respaldos.log` y borran los
de más de 14 días. Nunca sobrescriben un respaldo y solo borran los viejos después de uno correcto.

**Con docker compose** el servicio `respaldo` lo hace solo: al arrancar (si aún no hay uno de hoy) y todos
los días a las 03:00. Los archivos quedan en `./respaldos`. Se ajusta con `BACKUP_TIME`, `BACKUP_KEEP_DAYS`
y `APP_TIME_ZONE`.

**En Linux o macOS** (necesita `pg_dump` de la misma versión de PostgreSQL o más nueva):

```bash
sh scripts/respaldo/respaldar.sh                                  # respaldo ahora
sh scripts/respaldo/restaurar.sh respaldos/ARCHIVO.dump prueba    # cargarlo en una base nueva para revisarlo
sh scripts/respaldo/restaurar.sh respaldos/ARCHIVO.dump --si      # reemplazar la base de Espacia
```

**En Windows** (PowerShell 5.1 o más nuevo; `-PgBin` es la carpeta `bin` de PostgreSQL si no está en el PATH):

```bat
scripts\respaldo\respaldar.bat -PgBin C:\ruta\pgsql\bin
scripts\respaldo\programar-respaldo.bat -Hora 13:00 -PgBin C:\ruta\pgsql\bin
scripts\respaldo\restaurar.bat -Archivo respaldos\ARCHIVO.dump -BaseNueva prueba
scripts\respaldo\restaurar.bat -Archivo respaldos\ARCHIVO.dump -Reemplazar
```

`programar-respaldo` crea una tarea en el Programador de tareas de Windows para el usuario actual (sin
permisos de administrador). Si el equipo estaba apagado a esa hora, respalda en cuanto se enciende, y
nunca hace más de un respaldo por día.

Antes de reemplazar la base, `restaurar` hace un respaldo del estado actual y restaura todo en una sola
transacción: si algo falla, la base queda como estaba. Detén el backend antes de reemplazarla.

La contraseña de la base se toma de `DB_PASSWORD` o `PGPASSWORD`. Para una tarea programada sin variables,
usa el archivo de contraseñas de PostgreSQL (`%APPDATA%\postgresql\pgpass.conf` en Windows, `~/.pgpass` en Linux).

> Guarda una copia fuera del equipo: si los respaldos quedan en el mismo disco que la base, se pierden juntos.
> Lo más simple es apuntar la carpeta de respaldos a OneDrive, Google Drive o un disco externo.

## Documentación de la API

Con el backend encendido, abre `http://localhost:8080/swagger-ui.html`: lista todas las rutas y permite probarlas desde el navegador.
Inicia sesión con `POST /api/v1/auth/login`, copia el `token` de la respuesta y pégalo en **Authorize**.
En producción puedes ocultarla con `API_DOCS=false`.

## Pruebas automáticas

```bash
./mvnw test
```

Usan una base de datos H2 en memoria, así que no necesitan Docker. Levantan el backend completo y lo llaman por HTTP para cubrir registro e inicio de sesión, bloqueo por intentos fallidos, permisos por rol, las reglas de reserva y las reservas simultáneas. En GitHub se ejecutan solas en cada push (`.github/workflows/ci.yml`).

## Configuración

Todo se configura con variables de entorno; los valores por defecto coinciden con `docker-compose.yml`.

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/bibliospace_db` | Conexión a PostgreSQL |
| `DB_USERNAME` | `postgres` | Usuario de la base de datos |
| `DB_PASSWORD` | `bibliospace` | Contraseña de la base de datos (también la usa `docker compose`) |
| `JWT_SECRET` | sin valor | Clave para firmar los tokens (mínimo 32 caracteres). Si no se define, se genera una al azar en cada arranque y las sesiones se cierran al reiniciar. **Defínela siempre en producción** |
| `CORS_ALLOWED_ORIGINS` | `localhost` y `127.0.0.1` en los puertos 5173 y 8443 | Orígenes del frontend, separados por coma. En desarrollo el frontend reenvía sus llamadas por su propio servidor, así que no hace falta agregar la IP de cada celular |
| `FRONTEND_URL` | `http://localhost:8443` | Dirección del frontend, usada en los enlaces de los correos |
| `ADMIN_NOMBRE`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | valores de desarrollo | Administrador inicial. **Defínelos en producción** |
| `SEED_DEMO_DATA` | `true` | Crear datos de demostración |
| `SEED_DEMO_PASSWORD` | valor de desarrollo | Contraseña de las cuentas de demostración |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | vacío | Cuenta SMTP. Sin ellas el envío queda desactivado |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM_NAME` | `smtp.gmail.com`, `587`, `Espacia` | Servidor SMTP y nombre del remitente |
| `VERIFY_TOKEN_HOURS` | `24` | Horas que dura el enlace para confirmar el correo al registrarse |
| `UNVERIFIED_ACCOUNT_DAYS` | `7` | Días tras los que se borran las cuentas que nunca confirmaron su correo |
| `BACKUP_TIME`, `BACKUP_KEEP_DAYS` | `03:00`, `14` | Hora del respaldo diario de docker compose y días que se conservan |
| `REMINDER_MINUTES` | `60` | Minutos antes del inicio en que se envía el recordatorio |
| `APP_TIME_ZONE` | `America/Bogota` | Zona horaria de la biblioteca. Las reglas de horario la usan aunque el servidor esté en otra zona |
| `API_DOCS` | `true` | Muestra la documentación de la API en `/swagger-ui.html` |

## Roles

| Rol | Puede |
|---|---|
| Estudiante (`ROLE_ESTUDIANTE`) | Registrarse, ver el catálogo, reservar, cancelar, extender y ver sus notificaciones |
| Funcionario (`ROLE_FUNCIONARIO`) | Lo anterior, más validar llegadas, registrar devoluciones, ver todas las reservas y gestionar incidencias |
| Administrador (`ROLE_ADMIN`) | Todo lo anterior, más el dashboard, la gestión de espacios y la de usuarios |

El registro público siempre crea estudiantes. Las cuentas de funcionario y administrador las crea un administrador.

## Reglas de reserva

El administrador las cambia desde **Configuración** en su panel (`PUT /api/v1/rules`) y se aplican de inmediato, sin reiniciar. Los valores de una instalación nueva son:

- Horario de 08:00 a 20:00, máximo 4 horas por reserva.
- Máximo 3 reservas activas por usuario y hasta 30 días de anticipación.
- Máximo 1 equipo tecnológico y 2 salas o talleres por usuario y por día.
- Cancelación hasta 1 hora antes del inicio. Extensión de 1 hora si el espacio y el usuario están libres en esa hora.
- Si nadie valida la llegada en los primeros 30 minutos, la reserva se marca como **no asistida**, el espacio se libera y el usuario recibe un aviso.
- No se permiten reservas superpuestas en un mismo espacio.
- Si un espacio pasa a mantenimiento o inactivo, sus reservas futuras se anulan y se notifica a los usuarios.
- Las cuentas que se registran confirman su correo con un enlace (vence en 24 horas) antes de poder entrar. Las que crea el administrador no lo necesitan.
- Los correos de los dominios institucionales (por defecto `amigo.edu.co` y sus subdominios) se marcan como institucionales. Se cambian en **Configuración**.
- Las contraseñas deben tener al menos 8 caracteres.
- Tras 5 contraseñas incorrectas seguidas, la cuenta se bloquea 15 minutos. La recuperación de contraseña admite 3 solicitudes por hora por correo.

## Historial de cambios

Ver [CHANGELOG.md](CHANGELOG.md).

## API

Todas las rutas empiezan por `/api/v1`. Los errores responden `{"message": "..."}` (algunos agregan `"code"`).

| Método y ruta | Rol | Descripción |
|---|---|---|
| `POST /auth/register` · `POST /auth/login` | Público | Registro (estudiante) e inicio de sesión. El registro exige `"aceptaPrivacidad": true`, guarda la fecha y la versión de la política aceptada (`versionPolitica`) y envía el enlace para confirmar el correo; no devuelve token. El login devuelve el token JWT, o 403 con `"code": "EMAIL_NO_VERIFICADO"` si falta confirmar el correo |
| `POST /auth/verify-email` · `POST /auth/verify-email/check` · `POST /auth/resend-verification` | Público | Confirmar el correo con el enlace y la contraseña (devuelve el token), revisar si un enlace sigue vigente y reenviarlo |
| `POST /auth/forgot-password` · `POST /auth/reset-password` | Público | Recuperación de contraseña por correo (el enlace vence en 60 minutos y se usa una sola vez) |
| `GET /auth/me` · `PATCH /auth/password` | Autenticado | Datos propios y cambio de contraseña |
| `GET /spaces` · `GET /spaces/{id}` | Autenticado | Catálogo, con ocupación en tiempo real |
| `GET /spaces/{id}/availability?fecha=` | Autenticado | Horarios ocupados de un día |
| `POST /spaces` · `PUT /spaces/{id}` · `DELETE /spaces/{id}` | Admin | Gestión de espacios |
| `POST /reservations` · `GET /reservations/me` | Autenticado | Crear y listar reservas propias |
| `PATCH /reservations/{id}/cancel` · `/extend` | Dueño | Cancelar o extender 1 hora |
| `GET /reservations?estado=&fecha=` | Funcionario, admin | Todas las reservas |
| `PATCH /reservations/{id}/check-in` · `/finish` · `/reject` · `/no-show` | Funcionario, admin | Validar llegada, finalizar o devolver, anular, marcar como no asistida |
| `GET /notifications/me` · `PATCH /notifications/{id}/read` · `/read-all` | Autenticado | Notificaciones |
| `GET /users` · `POST /users` · `PATCH /users/{id}` · `PATCH /users/{id}/password` · `PATCH /users/{id}/verify-email` | Admin | Gestión de usuarios y confirmación manual del correo |
| `GET /incidents` · `POST /incidents` · `PATCH /incidents/{id}/resolve` | Funcionario, admin | Soporte técnico |
| `GET /admin/dashboard` | Admin | Estadísticas |
| `GET /rules` · `PUT /rules` | Público · Admin | Reglas de reserva vigentes y su modificación |

## Licencia

Copyright © 2026 Sebxx66. Todos los derechos reservados. El código se publica solo para consulta; ver [LICENSE](LICENSE).

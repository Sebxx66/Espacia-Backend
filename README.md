# Espacia — Backend

API REST para reservar salas de estudio, equipos y talleres de una biblioteca.
Spring Boot 4 · Java 25 · Spring Security + JWT · PostgreSQL.

El frontend está en el repositorio [Espacia-Frontend](https://github.com/Sebxx66/Espacia-Frontend).

> **Demo en línea:** [espacia.onrender.com](https://espacia.onrender.com) · API: [espacia-backend.onrender.com](https://espacia-backend.onrender.com/api/v1/rules)
> Corre en planes gratis: backend en Render (siempre despierto gracias a UptimeRobot) y base de datos en Supabase.

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
- Protección contra abuso: bloqueo temporal tras varios intentos fallidos (por cuenta y por red), topes de registros y de correos por red y en total, y verificación del correo antes del primer ingreso.
- Registro de auditoría: quién cambió reglas, usuarios, espacios, anulaciones e incidencias, cuándo y desde qué red.

## Seguridad

| Medida | Cómo |
|---|---|
| Contraseñas | BCrypt; las nuevas no pueden ser comunes ni contener el nombre o el correo (`PasswordPolicy`) |
| Sesiones | JWT firmado; cambiar la contraseña cierra las demás sesiones |
| Correo verificado | Las cuentas nuevas no entran hasta abrir el enlace del correo; las que nunca lo hacen se borran a los 7 días |
| Fuerza bruta | 5 contraseñas incorrectas bloquean la cuenta 15 minutos; 30 desde una misma red (aunque sean de cuentas distintas) bloquean esa red 15 minutos |
| Bots de registro | Máximo 20 cuentas nuevas por hora desde una misma red y 100 en total, y 20 correos de verificación o recuperación por hora por red. Son amplios porque en una universidad muchos estudiantes comparten la red; se ajustan con variables (ver *Configuración*) |
| Cabeceras HTTP | `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`, HSTS detrás de HTTPS y `Content-Security-Policy` en la API |
| Auditoría | Las acciones del panel quedan en `audit_log` con usuario, fecha e IP; el administrador las consulta en *Auditoría* |
| Base de datos | Sin acceso público: RLS activado en Supabase y conexión cifrada (`sslmode=require`) |

Para saber la IP real detrás de Render se usa la cabecera `CF-Connecting-IP`, que pone Cloudflare (el proxy de Render) y que el visitante no puede falsificar. En un servidor sin proxy se usa la IP de la conexión.

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

## Despliegue en Render

Espacia está publicada en [Render](https://render.com) con esta arquitectura:

```
Navegador ──► espacia.onrender.com          Static Site (frontend React compilado)
                 │  VITE_API_URL
                 ▼
             espacia-backend.onrender.com   Web Service con Docker (este repositorio)
                 │  TLS (Session pooler)             │  HTTPS (API)
                 ▼                                    ▼
             Supabase (PostgreSQL 17, East US)   Brevo (correos)

UptimeRobot consulta /api/v1/rules cada 5 minutos para que el backend no se duerma.
```

El `Dockerfile` compila el backend y crea una imagen con Java 25 lista para producción: usuario sin privilegios,
memoria ajustada a servidores pequeños (512 MB) y el puerto que indique la variable `PORT` (8080 por defecto).
Los planes gratis de Render y Railway bloquean el correo por SMTP, por eso en Render los correos salen por la API de
Brevo (`MAIL_PROVIDER=brevo`).

### Base de datos en Supabase

La base gratis de Render se borra a los 30 días, así que la base vive en [Supabase](https://supabase.com) (plan gratis:
500 MB, sin límite de horas de uso; se pausa solo si pasa 7 días sin ninguna consulta, lo que no ocurre con el backend encendido).

1. *New project* en la región **East US (North Virginia)**, la misma del backend en Render, y genera una contraseña.
2. En **Connect → Direct → Session pooler**, tipo JDBC, están el host y el usuario. Usa el *Session pooler*: la conexión
   directa solo funciona por IPv6 y Render usa IPv4.
3. Variables del backend en Render:

   | Variable | Valor |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?sslmode=require` |
   | `DB_USERNAME` | `postgres.REFERENCIA_DEL_PROYECTO` |
   | `DB_PASSWORD` | La contraseña del proyecto |

Al arrancar, el backend crea las tablas que falten. Para traer los datos de otra base (por ejemplo, la de Render) se copian
tablas, llaves, índices y filas en una sola transacción y se activa *Row Level Security* en cada tabla: Espacia se conecta
como dueño de las tablas y no se ve afectado, pero así nadie puede leerlas por la API pública de Supabase.
Conviene además apagar esa API en *Project Settings → Data API*, porque Espacia no la usa.

### Mantenerla despierta

En el plan gratis, Render duerme el backend a los 15 minutos sin visitas: la siguiente tarda cerca de un minuto y mientras
duerme no se envían recordatorios ni se borran las cuentas sin confirmar. Un monitor HTTP gratuito de
[UptimeRobot](https://uptimerobot.com) a `https://espacia-backend.onrender.com/api/v1/rules` cada 5 minutos lo mantiene
despierto (las 750 horas gratis al mes de Render alcanzan para un servicio encendido todo el mes) y avisa por correo si se cae.

### Opción A: de un solo paso con `render.yaml`

`render.yaml` describe una base de datos de Render, el backend y el frontend (sirve para una instalación de prueba;
para dejarla en Supabase cambia después las tres variables `DB_` como arriba). En Render: **New → Blueprint**, elige este
repositorio y completa las variables que pide (administrador, dirección del frontend, clave y remitente de Brevo,
dirección del backend para el frontend). Render crea la base, genera `JWT_SECRET` y conecta la base al backend solo.
Cambia los nombres de los servicios si ya existe una instalación con esos nombres en la misma cuenta.

### Opción B: paso a paso desde el panel

1. **Base de datos:** *New → Postgres*. Nombre `espacia-db`, base `espacia`, usuario `espacia`, región Virginia,
   versión 17. En *Connect → Internal* están el host (`dpg-...-a`) y la contraseña.
2. **Backend:** *New → Web Service* con este repositorio. Render detecta el `Dockerfile`. Misma región que la base,
   *Health Check Path* `/api/v1/rules` y estas variables:

   | Variable | Valor |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://HOST_INTERNO:5432/espacia` (o `DB_HOST`, `DB_PORT` y `DB_NAME` por separado) |
   | `DB_USERNAME`, `DB_PASSWORD` | Usuario y contraseña de la base |
   | `JWT_SECRET` | Botón *Generate* (32 caracteres o más) |
   | `ADMIN_NOMBRE`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Administrador inicial, con una contraseña fuerte |
   | `SEED_DEMO_DATA` | `false` (sin cuentas de demostración) |
   | `APP_TIME_ZONE` | `America/Bogota` |
   | `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | Dirección pública del frontend, sin `/` al final |
   | `MAIL_PROVIDER`, `MAIL_API_KEY`, `MAIL_FROM` | `brevo`, la clave de la API y el remitente verificado |

3. **Frontend:** *New → Static Site* con el repositorio del frontend (ver su README).
4. **Correo con Brevo (gratis, 300 correos al día):** verifica el teléfono de la cuenta; en *Remitentes* agrega y
   confirma el correo remitente; en *SMTP y API → Claves API* crea una clave con acceso restringido solo a
   *Emails transaccionales*. No actives el bloqueo por IP: Render cambia de IP.

### Tener en cuenta

- **Plan gratis:** sirve para la demo y para una biblioteca pequeña. Para una institución con muchos usuarios: backend
  *Starter* de Render (sin límite de horas, más memoria) y el plan *Pro* de Supabase (copias diarias automáticas).
- **IP real de los visitantes:** Render está detrás de Cloudflare, que pone la IP en `CF-Connecting-IP`. Si se despliega
  en otro servidor con otro proxy, cambia `CLIENT_IP_HEADER` (o déjala vacía sin proxy).
- **Cada push a `main` se publica solo.** La integración continua construye la misma imagen de Docker y comprueba que arranca.
- **Datos iniciales:** la base de Render empieza vacía. Los espacios se crean desde el panel del administrador.

## Documentación de la API

Con el backend encendido, abre `http://localhost:8080/swagger-ui.html`: lista todas las rutas y permite probarlas desde el navegador.
Inicia sesión con `POST /api/v1/auth/login`, copia el `token` de la respuesta y pégalo en **Authorize**.
En producción puedes ocultarla con `API_DOCS=false`.

## Pruebas automáticas

```bash
./mvnw test
```

Usan una base de datos H2 en memoria, así que no necesitan Docker. Levantan el backend completo y lo llaman por HTTP para cubrir registro e inicio de sesión, bloqueo por intentos fallidos (por cuenta y por red), cabeceras de seguridad, auditoría, permisos por rol, las reglas de reserva y las reservas simultáneas. En GitHub se ejecutan solas en cada push (`.github/workflows/ci.yml`).

## Configuración

Todo se configura con variables de entorno; los valores por defecto coinciden con `docker-compose.yml`.

| Variable | Por defecto | Uso |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/bibliospace_db` | Conexión a PostgreSQL. Si no se define, se arma con `DB_HOST`, `DB_PORT` y `DB_NAME` (`localhost`, `5432`, `bibliospace_db`) |
| `DB_USERNAME` | `postgres` | Usuario de la base de datos |
| `DB_PASSWORD` | `bibliospace` | Contraseña de la base de datos (también la usa `docker compose`) |
| `JWT_SECRET` | sin valor | Clave para firmar los tokens (mínimo 32 caracteres). Si no se define, se genera una al azar en cada arranque y las sesiones se cierran al reiniciar. **Defínela siempre en producción** |
| `CORS_ALLOWED_ORIGINS` | `localhost` y `127.0.0.1` en los puertos 5173 y 8443 | Orígenes del frontend, separados por coma. En desarrollo el frontend reenvía sus llamadas por su propio servidor, así que no hace falta agregar la IP de cada celular |
| `FRONTEND_URL` | `http://localhost:8443` | Dirección del frontend, usada en los enlaces de los correos |
| `ADMIN_NOMBRE`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | valores de desarrollo | Administrador inicial. **Defínelos en producción** |
| `SEED_DEMO_DATA` | `true` | Crear datos de demostración |
| `SEED_DEMO_PASSWORD` | valor de desarrollo | Contraseña de las cuentas de demostración |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | vacío | Cuenta SMTP. Sin ellas el envío queda desactivado |
| `MAIL_PROVIDER` | `smtp` | `smtp`, `brevo` o `resend`. Con `brevo` o `resend` los correos salen por la API HTTPS del servicio, necesario en servidores que bloquean SMTP (planes gratis de Render y Railway) |
| `MAIL_API_KEY`, `MAIL_FROM` | vacío | Clave de la API de Brevo o Resend y correo remitente verificado en ese servicio |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM_NAME` | `smtp.gmail.com`, `587`, `Espacia` | Servidor SMTP y nombre del remitente |
| `VERIFY_TOKEN_HOURS` | `24` | Horas que dura el enlace para confirmar el correo al registrarse |
| `UNVERIFIED_ACCOUNT_DAYS` | `7` | Días tras los que se borran las cuentas que nunca confirmaron su correo |
| `BACKUP_TIME`, `BACKUP_KEEP_DAYS` | `03:00`, `14` | Hora del respaldo diario de docker compose y días que se conservan |
| `REMINDER_MINUTES` | `60` | Minutos antes del inicio en que se envía el recordatorio |
| `APP_TIME_ZONE` | `America/Bogota` | Zona horaria de la biblioteca. Las reglas de horario la usan aunque el servidor esté en otra zona |
| `API_DOCS` | `true` | Muestra la documentación de la API en `/swagger-ui.html`. En producción, `false` |
| `LIMITE_REGISTROS_POR_IP`, `LIMITE_REGISTROS_POR_HORA` | `20`, `100` | Cuentas nuevas por hora desde una misma red y en total |
| `LIMITE_CORREOS_POR_IP` | `20` | Correos de verificación o recuperación por hora desde una misma red |
| `LIMITE_FALLOS_POR_IP` | `30` | Contraseñas incorrectas en 15 minutos desde una misma red antes de bloquearla 15 minutos |
| `CLIENT_IP_HEADER` | `CF-Connecting-IP` | Cabecera con la IP real del visitante cuando hay un proxy delante. Vacía = IP de la conexión |

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
- Las contraseñas nuevas deben tener al menos 8 caracteres y no pueden ser comunes o predecibles (unas 2.100 conocidas, palabras como "espacia" o "contraseña" seguidas de números, secuencias como "12345678") ni contener el nombre o el correo de la persona. Las contraseñas que ya existían siguen funcionando.
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

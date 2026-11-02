# Historial de cambios

Cambios del backend de Espacia, del más reciente al más antiguo. Cada entrada explica qué cambió y cómo está hecho.

## 1.2.0 — Sin claves en el repositorio

- **La clave de los tokens ya no está en el código.** `jwt.secret` se toma solo de la variable de entorno `JWT_SECRET`. Si no está definida, `JwtTokenProvider` genera al arrancar una clave aleatoria de 512 bits con `SecureRandom` y avisa en el log: sirve para desarrollo, pero las sesiones se cierran al reiniciar el backend. Si `JWT_SECRET` tiene menos de 32 caracteres, el backend no arranca y lo explica. La clave se calcula una sola vez al iniciar, en lugar de en cada token.
- **El README ya no publica cuentas ni contraseñas de prueba.** Se quitó la tabla de cuentas de demostración y la contraseña del administrador inicial; ahora se indica definir `ADMIN_EMAIL` y `ADMIN_PASSWORD` antes del primer arranque.

## 1.1.1 — Prueba de horas pasadas

- El backend ya rechazaba reservas cuya hora de inicio ya pasó (`ReservationService.create` compara fecha y hora de inicio con el momento actual y responde 400 con "No puedes reservar en una fecha u hora que ya pasó."). Se agrega la prueba `rechazaHorasDeHoyQueYaPasaron`, que intenta reservar la hora en curso del mismo día; antes solo se probaban días anteriores.

## 1.1.0 — Nuevo nombre: Espacia

- Los correos se envían con el nombre **Espacia**: asunto, firma y nombre del remitente (`MAIL_FROM_NAME`, por defecto `Espacia`).
- La cabecera de los correos muestra la marca como una insignia hecha con HTML y estilos en línea, en lugar de un emoji, para que se vea igual en Gmail, Outlook y el correo del celular.
- Los textos de los datos de demostración usan el nuevo nombre.
- Se mantienen los identificadores técnicos (paquete `com.bibliospace`, base de datos `bibliospace_db`, cuentas `@bibliospace.local`) para no romper instalaciones ni datos existentes.

## 1.0.0 — Versión inicial

- **Límite diario de reservas.** Además de las 3 reservas activas, cada usuario puede tener por día 1 equipo tecnológico y 2 salas o talleres. `ReservationService` cuenta las reservas activas del usuario en esa fecha, separando equipos (`EQUIPO_TECNOLOGICO`) del resto, y rechaza la nueva si supera `MAX_EQUIPOS_DIA` o `MAX_SALAS_DIA`.
- **Bloqueo por intentos fallidos.** `RateLimitService` guarda en memoria los fallos por correo: tras 5 contraseñas incorrectas en 15 minutos la cuenta queda bloqueada 15 minutos y el inicio de sesión responde `429`. La recuperación de contraseña admite 3 solicitudes por correo cada hora. Los contadores se reinician con el backend y están limitados a 5.000 entradas.
- **Recordatorios por correo.** `ReminderService` se ejecuta cada minuto (`@Scheduled`) y busca reservas confirmadas que empiezan dentro de `REMINDER_MINUTES` (60 por defecto). Envía el aviso y marca `recordatorioEnviado` en la reserva para no repetirlo.
- **Correos con diseño propio.** `EmailService` arma el HTML con estilos en línea (bienvenida, confirmación, cancelación o anulación, recordatorio y recuperación de contraseña) e incluye un botón que lleva al frontend (`FRONTEND_URL`). El envío es asíncrono (`@Async`), así que no retrasa las respuestas de la API. Sin `MAIL_USERNAME` y `MAIL_PASSWORD`, los correos solo se registran en el log.
- **Datos de demostración.** `DataSeeder` crea espacios, estudiantes, un funcionario, reservas de las últimas semanas, incidencias y notificaciones la primera vez que arranca (se desactiva con `SEED_DEMO_DATA=false`).
- **Pruebas automáticas.** Pruebas de integración con H2 en memoria que levantan el backend completo: registro e inicio de sesión, bloqueo por intentos, permisos por rol, reglas de reserva (incluido el límite diario) y reservas simultáneas. Se ejecutan en GitHub Actions en cada push.

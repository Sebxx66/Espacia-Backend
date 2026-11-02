# Historial de cambios

Cambios del backend de Espacia, del más reciente al más antiguo. Cada entrada explica qué cambió y cómo está hecho.

## 1.7.0 — Verificación del correo al registrarse

- **Las cuentas nuevas confirman su correo con un enlace.** `POST /auth/register` ya no inicia sesión: crea la cuenta con `emailVerificado = false` y envía un correo con un enlace de un solo uso que vence en 24 horas (`VERIFY_TOKEN_HOURS`). Solo se guarda el hash SHA-256 del enlace. Mientras no se confirme, `POST /auth/login` con la contraseña correcta responde 403 con `"code": "EMAIL_NO_VERIFICADO"`, para que el frontend ofrezca reenviar el enlace; con la contraseña incorrecta sigue respondiendo 401, así no se revela si la cuenta existe.
- **El enlace se activa con la contraseña del registro.** `POST /auth/verify-email` recibe el token y la contraseña, marca el correo como confirmado, envía la bienvenida y devuelve la sesión iniciada. Pedir la contraseña evita que alguien que se registró con el correo de otra persona logre activar esa cuenta: el dueño del correo recibe el enlace, pero no conoce esa contraseña. Los intentos fallidos cuentan para el bloqueo de 15 minutos del login. `POST /auth/verify-email/check` revisa si un enlace sigue vigente.
- **Reenviar y volver a registrarse.** `POST /auth/resend-verification` envía un enlace nuevo y anula el anterior (responde 204 exista o no la cuenta). Registrarse de nuevo con un correo sin confirmar reemplaza el nombre, la contraseña y el enlace. Ambos están limitados a 3 enlaces por hora por correo.
- **Cuentas sin confirmar.** `UnverifiedAccountCleanup` borra cada madrugada las que llevan más de 7 días sin confirmar (`UNVERIFIED_ACCOUNT_DAYS`), para que un correo mal escrito o usado por otra persona no quede ocupado. El administrador puede confirmar un correo a mano con `PATCH /users/{id}/verify-email`.
- **Cuentas existentes y creadas por el administrador** no necesitan confirmar: las anteriores a esta versión quedan con `emailVerificado` vacío (se toman como confirmadas) y las que crea el administrador se guardan confirmadas.
- **El enlace abre donde la persona se registró.** El frontend envía su dirección (`appUrl`) y `AppUrlResolver` la acepta solo si es `FRONTEND_URL`, un origen de `CORS_ALLOWED_ORIGINS`, `localhost` o una IP de red local (10.x, 172.16-31.x, 192.168.x), sin ruta ni parámetros. Así funciona desde el celular en la misma red y nadie puede hacer que el correo apunte a un sitio externo.
- **Dominios institucionales.** Las reglas tienen el campo `dominiosInstitucionales` (por defecto `amigo.edu.co`), editable en `PUT /rules`. Se normaliza (minúsculas, sin `@`, sin repetidos) y se valida. Cuentan también los subdominios: con `amigo.edu.co`, `ana@est.amigo.edu.co` es institucional. `GET /users` y el registro indican si cada correo es `institucional`; todos confirman su correo igual.
- **Errores con código.** `ApiException` admite un `code` opcional que se agrega a la respuesta (`{"message": "...", "code": "..."}`).
- El recordatorio por correo usa las horas de cancelación configuradas en lugar de "1 hora" fijo.
- Pruebas nuevas en `EmailVerificationApiTests` (con el correo simulado para leer el enlace): activación con la contraseña correcta, enlace de un solo uso, dirección del enlace segura, nuevo registro que reemplaza al pendiente, reenvío, límite de enlaces, confirmación manual y cuentas creadas por el administrador. `RulesApiTests` prueba los dominios institucionales. Las demás pruebas usan `registerVerified` para crear cuentas ya confirmadas.

## 1.6.0 — Reglas de reserva configurables

- **Las reglas ya no están fijas en el código.** Nueva entidad `BookingRules` (tabla `booking_rules`, una sola fila) con horario de apertura y cierre, duración máxima, reservas activas, límites diarios de equipos y de salas, horas para cancelar, minutos para liberar una no asistida y días de anticipación. Una instalación nueva arranca con los valores de siempre (8 a 20 h, 4 horas, 3 activas, 1 equipo y 2 salas por día, 1 hora, 30 minutos, 30 días).
- **`BookingRulesService`** las guarda en la base de datos y las mantiene en memoria, porque se consultan en cada reserva. Al cambiarlas se aplican de inmediato, sin reiniciar. Valida que tengan sentido: el cierre después de la apertura, la duración máxima dentro del horario y al menos un tipo de espacio reservable.
- **API:** `GET /api/v1/rules` es público (la pantalla de inicio y los Términos de uso muestran las reglas vigentes) y `PUT /api/v1/rules` es solo para administradores. Se guarda quién y cuándo las cambió (`actualizadoPor` no se publica).
- `ReservationService`, `NoShowService` y el dashboard usan las reglas vigentes. Los mensajes de error muestran los valores reales, por ejemplo "El horario de reservas es de 07:00 a 22:00". La propiedad `app.no-show-minutes` deja de usarse.
- Pruebas nuevas en `RulesApiTests`: reglas públicas, cambio aplicado de inmediato, reglas incoherentes rechazadas y permiso solo para administradores.

## 1.5.0 — Autorización de tratamiento de datos

- **El registro exige autorizar el tratamiento de datos personales** (Ley 1581 de 2012). `RegisterRequest` tiene el campo obligatorio `aceptaPrivacidad` (`@AssertTrue`); sin él la API responde 400 y no crea la cuenta.
- **Prueba de la autorización.** `User` guarda `privacidadAceptadaEn` (fecha y hora) y `privacidadVersion` (versión de la política aceptada, enviada por el frontend en `versionPolitica`).
- Prueba nueva: `registroExigeAutorizarElTratamientoDeDatos`.

## 1.4.0 — No asistidas, zona horaria y documentación de la API

- **Reservas no asistidas.** Nuevo estado `NO_ASISTIDA`. `NoShowService` revisa cada minuto las reservas de hoy confirmadas y, si nadie validó la llegada a los `NO_SHOW_MINUTES` (30 por defecto) del inicio, las marca como no asistidas: el horario queda libre y el usuario recibe una notificación y un correo. El personal también puede marcarlo a mano con `PATCH /reservations/{id}/no-show`, solo cuando la reserva ya empezó; antes de esa hora se usa "anular". Las no asistidas siguen contando para el límite diario, para que no se pueda reservar y faltar sin consecuencia.
- **Estado nuevo en bases existentes.** Hibernate crea en PostgreSQL una restricción `CHECK` con los estados que existían al crear la tabla, y `ddl-auto=update` no la actualiza. `EstadoReservaConstraint` la reemplaza al arrancar por una con todos los estados actuales (es seguro repetirlo).
- **Zona horaria fija.** `BackendApplication` fija la zona de la aplicación en `America/Bogota` (configurable con la variable de entorno `APP_TIME_ZONE`) antes de arrancar cualquier componente, y las pruebas corren con la misma zona (`maven-surefire-plugin`). Sin esto, en un servidor en la nube (normalmente en UTC) las horas pasadas, los recordatorios y las no asistidas se calcularían con 5 horas de diferencia.
- **Documentación interactiva de la API.** Con springdoc-openapi, `/swagger-ui.html` muestra todas las rutas y permite probarlas con el token (botón Authorize). Se puede ocultar con `API_DOCS=false`.
- **Licencia.** Todos los derechos reservados (archivo `LICENSE`).
- La versión del proyecto pasa a `1.4.0` en `pom.xml`, alineada con este historial.
- Pruebas nuevas: liberación automática a los 30 minutos, marcado manual solo después del inicio y acceso público a la documentación.

## 1.3.0 — Sesiones y extensión de reservas

- **Cambiar la contraseña cierra las sesiones abiertas.** `User` guarda `passwordCambiadaEn` cada vez que cambia la contraseña: al cambiarla desde la app, al restablecerla con el enlace del correo o cuando la restablece un administrador. `JwtAuthenticationFilter` compara esa fecha con la hora de emisión del token (`iat`) y deja de aceptar los tokens emitidos antes. Quien cambia su propia contraseña recibe un token nuevo en la respuesta de `PATCH /auth/password`, así que sigue conectado en ese dispositivo y se cierran los demás.
- **Extender ya no puede quedar encima de otra reserva propia.** Antes solo se revisaba que el espacio estuviera libre en la hora siguiente. Ahora también se rechaza (409) si el usuario tiene otra reserva en ese horario, por ejemplo un equipo justo a continuación de una sala.
- Pruebas nuevas: `cambiarLaContrasenaCierraLasDemasSesiones`, `restablecerLaContrasenaCierraLasSesionesDelUsuario` y `noSePuedeExtenderEncimaDeOtraReservaPropia`.

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

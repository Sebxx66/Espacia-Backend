package com.bibliospace.backend.services;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Envío de correos. Dos formas, según MAIL_PROVIDER:
//   smtp (por defecto): por SMTP, Gmail por defecto, con MAIL_USERNAME y MAIL_PASSWORD.
//   brevo o resend: por la API HTTPS del servicio, con MAIL_API_KEY y MAIL_FROM. Sirve en servidores
//   que bloquean SMTP, como los planes gratis de Render y Railway.
// Sin la configuración necesaria el envío queda desactivado: la app funciona igual y solo escribe
// en el log lo que habría enviado. Todos los métodos públicos son asíncronos para no retrasar la respuesta.
@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final String username;
    private final String fromName;
    private final String frontendUrl;
    private final String provider;
    private final String apiKey;
    private final String from;
    private final String apiUrl;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${spring.mail.username:}") String username,
                        @Value("${app.mail.from-name:Espacia}") String fromName,
                        @Value("${app.frontend-url:http://localhost:8443}") String frontendUrl,
                        @Value("${app.mail.provider:smtp}") String provider,
                        @Value("${app.mail.api-key:}") String apiKey,
                        @Value("${app.mail.from:}") String from,
                        @Value("${app.mail.api-url:}") String apiUrl) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.username = username == null ? "" : username.trim();
        this.fromName = fromName;
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
        this.provider = provider == null ? "smtp" : provider.trim().toLowerCase(Locale.ROOT);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        // Remitente: MAIL_FROM, o la cuenta SMTP si no se definió
        this.from = from == null || from.isBlank() ? this.username : from.trim();
        // Dirección de la API: configurable solo para pruebas
        this.apiUrl = apiUrl != null && !apiUrl.isBlank() ? apiUrl.trim()
                : switch (this.provider) {
                    case "brevo" -> "https://api.brevo.com/v3/smtp/email";
                    case "resend" -> "https://api.resend.com/emails";
                    default -> "";
                };
        if (!this.provider.equals("smtp") && this.apiUrl.isEmpty()) {
            log.warn("MAIL_PROVIDER={} no es válido (usa smtp, brevo o resend): el correo queda desactivado", provider);
        }
    }

    private boolean porApi() {
        return provider.equals("brevo") || provider.equals("resend");
    }

    public boolean enabled() {
        if (porApi()) {
            return !apiKey.isBlank() && !from.isBlank();
        }
        return provider.equals("smtp") && mailSender != null && !username.isBlank();
    }

    @Async
    public void sendWelcome(String to, String nombre) {
        deliver(to, "¡Bienvenido/a a Espacia!", layout(
                "¡Bienvenido/a, " + esc(nombre) + "!",
                "<p>Confirmaste tu correo y tu cuenta en Espacia está lista. Ya puedes iniciar sesión para reservar "
                        + "salas de estudio, equipos y talleres de la biblioteca.</p>"
                        + boton(frontendUrl + "/", "Iniciar sesión")
                        + "<p style=\"font-size:13px;color:#6b7280\">Si no creaste esta cuenta, ignora este mensaje.</p>"));
    }

    @Async
    public void sendVerification(String to, String nombre, String link, int horas) {
        if (!enabled()) {
            // Solo en desarrollo, sin correo configurado: así se puede activar la cuenta sin SMTP
            log.info("[correo desactivado] Enlace para confirmar el correo de {}: {}", to, link);
            return;
        }
        deliver(to, "Confirma tu correo · Espacia", layout(
                "Confirma tu correo",
                "<p>Hola, " + esc(nombre) + ".</p>"
                        + "<p>Para terminar de crear tu cuenta en Espacia, confirma que este correo es tuyo. "
                        + "Pulsa el botón y escribe la contraseña que elegiste al registrarte. "
                        + "El enlace vence en " + horas + " horas.</p>"
                        + boton(link, "Confirmar mi correo")
                        + "<p style=\"font-size:12px;color:#6b7280\">Si el botón no funciona, copia este enlace en tu navegador:<br>"
                        + esc(link) + "</p>"
                        + "<p>Si no creaste esta cuenta, ignora este correo: sin confirmar no se puede usar.</p>"));
    }

    @Async
    public void sendReservationNotice(String to, String nombre, String tipo, String mensaje) {
        String asunto = switch (tipo) {
            case "CONFIRMACION" -> "Reserva confirmada";
            case "CANCELACION" -> "Reserva cancelada";
            case "RECHAZADA" -> "Reserva anulada";
            case "NO_ASISTIDA" -> "Reserva no asistida";
            default -> "Novedad en tu reserva";
        };
        deliver(to, asunto + " · Espacia", layout(
                esc(asunto),
                "<p>Hola, " + esc(nombre) + ".</p><p>" + esc(mensaje) + "</p>"
                        + boton(frontendUrl + "/", "Ver mis reservas")));
    }

    @Async
    public void sendReminder(String to, String nombre, String mensaje) {
        deliver(to, "Recordatorio de tu reserva · Espacia", layout(
                "Tu reserva empieza pronto",
                "<p>Hola, " + esc(nombre) + ".</p><p>" + esc(mensaje) + "</p>"
                        + boton(frontendUrl + "/", "Ver mis reservas")));
    }

    @Async
    public void sendPasswordReset(String to, String nombre, String link, int minutos) {
        if (!enabled()) {
            // Solo en desarrollo, sin correo configurado: así se puede probar el flujo sin SMTP
            log.info("[correo desactivado] Enlace para restablecer la contraseña de {}: {}", to, link);
            return;
        }
        deliver(to, "Restablece tu contraseña · Espacia", layout(
                "Restablece tu contraseña",
                "<p>Hola, " + esc(nombre) + ".</p>"
                        + "<p>Recibimos una solicitud para cambiar la contraseña de tu cuenta. "
                        + "Pulsa el botón para elegir una nueva. El enlace vence en " + minutos + " minutos y solo se puede usar una vez.</p>"
                        + boton(link, "Cambiar contraseña")
                        + "<p style=\"font-size:12px;color:#6b7280\">Si el botón no funciona, copia este enlace en tu navegador:<br>"
                        + esc(link) + "</p>"
                        + "<p>Si no pediste este cambio, ignora este correo: tu contraseña actual sigue funcionando.</p>"));
    }

    private void deliver(String to, String subject, String html) {
        if (!enabled()) {
            log.info("[correo desactivado] Para: {} | Asunto: {}", to, subject);
            return;
        }
        if (porApi()) {
            enviarPorApi(to, subject, html);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(username, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Correo enviado a {} ({})", to, subject);
        } catch (Exception e) {
            // Un fallo de correo nunca debe romper el registro ni las reservas
            log.error("No se pudo enviar el correo a {}: {}", to, e.getMessage());
        }
    }

    // Brevo y Resend reciben el correo como JSON por HTTPS (puerto 443, que los servidores no bloquean)
    private void enviarPorApi(String to, String subject, String html) {
        try {
            Map<String, Object> cuerpo;
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (provider.equals("brevo")) {
                cuerpo = Map.of(
                        "sender", Map.of("name", fromName, "email", from),
                        "to", List.of(Map.of("email", to)),
                        "subject", subject,
                        "htmlContent", html);
                req.header("api-key", apiKey);
            } else {
                cuerpo = Map.of(
                        "from", fromName + " <" + from + ">",
                        "to", List.of(to),
                        "subject", subject,
                        "html", html);
                req.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> res = HTTP.send(
                    req.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 == 2) {
                log.info("Correo enviado a {} ({}) por {}", to, subject, provider);
            } else {
                String detalle = res.body() == null ? "" : res.body();
                log.error("No se pudo enviar el correo a {} por {}: HTTP {} {}", to, provider, res.statusCode(),
                        detalle.length() > 300 ? detalle.substring(0, 300) : detalle);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Envío de correo interrumpido para {}", to);
        } catch (Exception e) {
            // Un fallo de correo nunca debe romper el registro ni las reservas
            log.error("No se pudo enviar el correo a {} por {}: {}", to, provider, e.getMessage());
        }
    }

    private static String esc(String text) {
        return HtmlUtils.htmlEscape(text == null ? "" : text);
    }

    private static String boton(String url, String texto) {
        return "<p style=\"margin:24px 0\"><a href=\"" + esc(url) + "\" style=\"background:#4C6EF5;color:#ffffff;"
                + "padding:12px 24px;border-radius:10px;text-decoration:none;font-weight:600;display:inline-block\">"
                + esc(texto) + "</a></p>";
    }

    private static String layout(String titulo, String contenido) {
        return "<div style=\"background:#f3f5fb;padding:24px 12px;font-family:Segoe UI,Arial,sans-serif;color:#1f2937\">"
                + "<div style=\"max-width:560px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;"
                + "border:1px solid #e3e8f5\">"
                + "<div style=\"background:#4C6EF5;padding:22px 28px\">"
                + "<div style=\"font-size:22px;font-weight:700;color:#ffffff;letter-spacing:.3px\"><span style=\"display:inline-block;width:30px;height:30px;line-height:30px;text-align:center;background:#ffffff;color:#4C6EF5;border-radius:9px;margin-right:10px\">E</span>Espacia</div>"
                + "<div style=\"font-size:13px;color:#dbe4ff;margin-top:4px\">Reserva de espacios y recursos de la biblioteca</div>"
                + "</div>"
                + "<div style=\"padding:28px;font-size:15px;line-height:1.55\">"
                + "<h2 style=\"margin:0 0 16px;font-size:20px;color:#111827\">" + titulo + "</h2>"
                + contenido
                + "</div>"
                + "<div style=\"padding:16px 28px;background:#f8f9fd;border-top:1px solid #e3e8f5;font-size:12px;color:#6b7280\">"
                + "Sistema de Gestión Bibliotecaria · Este es un mensaje automático, no respondas a este correo."
                + "</div></div></div>";
    }
}

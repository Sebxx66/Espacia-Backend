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

// Envío de correos por SMTP (Gmail por defecto). Si no hay MAIL_USERNAME configurado, el envío
// queda desactivado: la app funciona igual y solo escribe en el log lo que habría enviado.
// Todos los métodos públicos son asíncronos para no retrasar la respuesta al usuario.
@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final String username;
    private final String fromName;
    private final String frontendUrl;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${spring.mail.username:}") String username,
                        @Value("${app.mail.from-name:BiblioSpace}") String fromName,
                        @Value("${app.frontend-url:http://localhost:8443}") String frontendUrl) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.username = username == null ? "" : username.trim();
        this.fromName = fromName;
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
    }

    public boolean enabled() {
        return mailSender != null && !username.isBlank();
    }

    @Async
    public void sendWelcome(String to, String nombre) {
        deliver(to, "¡Bienvenido/a a BiblioSpace!", layout(
                "¡Bienvenido/a, " + esc(nombre) + "!",
                "<p>Tu cuenta en BiblioSpace se creó correctamente. Ya puedes iniciar sesión para reservar "
                        + "salas de estudio, equipos y talleres de la biblioteca.</p>"
                        + boton(frontendUrl + "/", "Iniciar sesión")
                        + "<p style=\"font-size:13px;color:#6b7280\">Si no creaste esta cuenta, ignora este mensaje.</p>"));
    }

    @Async
    public void sendReservationNotice(String to, String nombre, String tipo, String mensaje) {
        String asunto = switch (tipo) {
            case "CONFIRMACION" -> "Reserva confirmada";
            case "CANCELACION" -> "Reserva cancelada";
            case "RECHAZADA" -> "Reserva anulada";
            default -> "Novedad en tu reserva";
        };
        deliver(to, asunto + " · BiblioSpace", layout(
                esc(asunto),
                "<p>Hola, " + esc(nombre) + ".</p><p>" + esc(mensaje) + "</p>"
                        + boton(frontendUrl + "/", "Ver mis reservas")));
    }

    @Async
    public void sendReminder(String to, String nombre, String mensaje) {
        deliver(to, "Recordatorio de tu reserva · BiblioSpace", layout(
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
        deliver(to, "Restablece tu contraseña · BiblioSpace", layout(
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
                + "<div style=\"font-size:22px;font-weight:700;color:#ffffff;letter-spacing:.3px\">&#128218; BiblioSpace</div>"
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

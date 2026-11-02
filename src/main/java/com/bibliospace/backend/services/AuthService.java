package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.request.ChangePasswordRequest;
import com.bibliospace.backend.dtos.request.LoginRequest;
import com.bibliospace.backend.dtos.request.ResetPasswordRequest;
import com.bibliospace.backend.dtos.request.RegisterRequest;
import com.bibliospace.backend.dtos.request.ResendVerificationRequest;
import com.bibliospace.backend.dtos.request.VerifyEmailRequest;
import com.bibliospace.backend.dtos.response.RegisterResponse;
import com.bibliospace.backend.dtos.response.AuthResponse;
import com.bibliospace.backend.dtos.response.UserResponse;
import com.bibliospace.backend.entities.PasswordResetToken;
import com.bibliospace.backend.entities.Role;
import com.bibliospace.backend.entities.User;
import com.bibliospace.backend.exceptions.ApiException;
import com.bibliospace.backend.repositories.PasswordResetTokenRepository;
import com.bibliospace.backend.repositories.UserRepository;
import com.bibliospace.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final CurrentUserService currentUserService;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;
    private final BookingRulesService bookingRulesService;
    private final AppUrlResolver appUrlResolver;

    public static final String CODIGO_SIN_VERIFICAR = "EMAIL_NO_VERIFICADO";

    // Dirección del frontend, para armar el enlace del correo de recuperación
    @Value("${app.frontend-url:http://localhost:8443}")
    private String frontendUrl;

    @Value("${app.reset-token-minutes:60}")
    private int resetTokenMinutes;

    // Vigencia del enlace para confirmar el correo
    @Value("${app.verify-token-hours:24}")
    private int verifyTokenHours;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Crea la cuenta sin sesión y envía el enlace para confirmar el correo.
    // Si ya existía una cuenta con ese correo que nunca se confirmó, se reemplazan sus datos
    // (la persona pudo equivocarse de contraseña u otra persona pudo registrarse con su correo).
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user != null && !user.correoPendiente()) {
            throw ApiException.conflict("Ya existe una cuenta registrada con ese email.");
        }
        if (!rateLimitService.allowPasswordReset("verificacion:" + email)) {
            throw ApiException.tooManyRequests("Ya enviamos varios enlaces a ese correo. Revisa tu bandeja (y la carpeta de spam) o inténtalo en una hora.");
        }
        if (user == null) {
            // Por defecto, los registros desde el frontend serán de ESTUDIANTE
            user = User.builder()
                    .email(email)
                    .rol(Role.ROLE_ESTUDIANTE)
                    .emailVerificado(false)
                    .build();
        }
        user.setNombre(request.getNombre().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPrivacidadAceptadaEn(LocalDateTime.now());
        user.setPrivacidadVersion(request.getVersionPolitica() == null || request.getVersionPolitica().isBlank()
                ? "1.0" : request.getVersionPolitica().trim());
        String token = nuevoEnlaceVerificacion(user);
        userRepository.save(user);
        enviarVerificacion(user, token, request.getAppUrl());

        return RegisterResponse.builder()
                .email(email)
                .verificacionPendiente(true)
                .institucional(bookingRulesService.esInstitucional(email))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        // Bloqueo temporal tras varios intentos fallidos (responde 429)
        rateLimitService.checkLogin(email);

        // Autentica las credenciales (si fallan, GlobalExceptionHandler responde 401 o 403)
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (BadCredentialsException e) {
            rateLimitService.loginFailed(email);
            throw e;
        }
        rateLimitService.loginOk(email);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Genera el token
        String token = jwtTokenProvider.generateToken(authentication);

        // Busca al usuario para devolver sus datos en la respuesta
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        // Contraseña correcta, pero el correo aún no se confirma: el frontend ofrece reenviar el enlace
        if (user.correoPendiente()) {
            SecurityContextHolder.clearContext();
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Confirma tu correo para entrar. Te enviamos un enlace a " + user.getEmail() + ".",
                    CODIGO_SIN_VERIFICAR);
        }

        return respuesta(user, token);
    }

    private static AuthResponse respuesta(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .nombre(user.getNombre())
                .email(user.getEmail())
                .rol(user.getRol().name())
                .build();
    }

    public UserResponse me() {
        User user = currentUserService.get();
        return UserService.toResponse(user, null, bookingRulesService.esInstitucional(user.getEmail()));
    }

    // --- Verificación del correo ---------------------------------------------

    // Revisa un enlace antes de mostrar el formulario (para avisar de una vez si ya venció)
    public Map<String, String> checkVerification(String token) {
        User user = usuarioDelEnlace(token);
        return Map.of("email", user.getEmail(), "nombre", user.getNombre());
    }

    // Activa la cuenta con el enlace del correo y la contraseña del registro, e inicia la sesión
    @Transactional
    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = usuarioDelEnlace(request.getToken());
        String email = user.getEmail();
        rateLimitService.checkLogin(email);
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            rateLimitService.loginFailed(email);
            throw ApiException.badRequest("La contraseña no coincide con la que usaste al registrarte. "
                    + "Si no la recuerdas, regístrate de nuevo con el mismo correo.");
        }
        rateLimitService.loginOk(email);
        if (!Boolean.TRUE.equals(user.getActivo())) {
            throw ApiException.forbidden("Tu cuenta está suspendida. Contacta al administrador.");
        }
        user.marcarCorreoVerificado();
        userRepository.save(user);
        emailService.sendWelcome(email, user.getNombre());
        return respuesta(user, jwtTokenProvider.generateTokenFor(email));
    }

    // Envía un enlace nuevo. Responde igual exista o no la cuenta, para no revelar qué correos están registrados.
    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String email = normalizeEmail(request.getEmail());
        userRepository.findByEmailIgnoreCase(email)
                .filter(User::correoPendiente)
                .filter(u -> rateLimitService.allowPasswordReset("verificacion:" + email))
                .ifPresent(user -> {
                    String token = nuevoEnlaceVerificacion(user);
                    userRepository.save(user);
                    enviarVerificacion(user, token, request.getAppUrl());
                });
    }

    private User usuarioDelEnlace(String token) {
        return userRepository.findByVerificacionTokenHash(sha256(token.trim()))
                .filter(u -> u.correoPendiente() && u.getVerificacionExpiraEn() != null
                        && u.getVerificacionExpiraEn().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> ApiException.badRequest(
                        "El enlace no es válido o ya venció. Pide uno nuevo desde la pantalla de ingreso."));
    }

    // Genera el enlace (solo se guarda su hash) y anula el anterior
    private String nuevoEnlaceVerificacion(User user) {
        String token = newToken();
        user.setVerificacionTokenHash(sha256(token));
        user.setVerificacionExpiraEn(LocalDateTime.now().plusHours(verifyTokenHours));
        return token;
    }

    private void enviarVerificacion(User user, String token, String appUrl) {
        String link = appUrlResolver.resolver(appUrl) + "/?verificar=" + token;
        emailService.sendVerification(user.getEmail(), user.getNombre(), link, verifyTokenHours);
    }

    // Cambia la contraseña, cierra las demás sesiones y devuelve un token nuevo para este dispositivo
    public String changePassword(ChangePasswordRequest request) {
        User user = currentUserService.get();
        if (!passwordEncoder.matches(request.getActual(), user.getPassword())) {
            throw ApiException.badRequest("La contraseña actual no es correcta.");
        }
        user.cambiarPassword(passwordEncoder.encode(request.getNueva()));
        userRepository.save(user);
        return jwtTokenProvider.generateTokenFor(user.getEmail());
    }

    // Siempre responde igual exista o no la cuenta, para no revelar qué emails están registrados
    @Transactional
    public void forgotPassword(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        // Demasiadas solicitudes: se ignora en silencio (la respuesta es igual para no revelar nada)
        if (!rateLimitService.allowPasswordReset(email)) {
            return;
        }
        userRepository.findByEmailIgnoreCase(email)
                .filter(u -> Boolean.TRUE.equals(u.getActivo()) && !u.correoPendiente())
                .ifPresent(user -> {
                    resetTokenRepository.invalidateAllForUser(user.getId());
                    String token = newToken();
                    resetTokenRepository.save(PasswordResetToken.builder()
                            .usuario(user)
                            .tokenHash(sha256(token))
                            .expiraEn(LocalDateTime.now().plusMinutes(resetTokenMinutes))
                            .build());
                    String link = frontendUrl.replaceAll("/+$", "") + "/?reset=" + token;
                    emailService.sendPasswordReset(user.getEmail(), user.getNombre(), link, resetTokenMinutes);
                });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = resetTokenRepository.findByTokenHash(sha256(request.getToken().trim()))
                .filter(t -> !Boolean.TRUE.equals(t.getUsado()) && t.getExpiraEn().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> ApiException.badRequest("El enlace no es válido o ya venció. Pide uno nuevo."));
        User user = token.getUsuario();
        if (!Boolean.TRUE.equals(user.getActivo())) {
            throw ApiException.forbidden("Tu cuenta está suspendida. Contacta al administrador.");
        }
        user.cambiarPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        // El enlace usado (y cualquier otro pendiente) deja de servir
        resetTokenRepository.invalidateAllForUser(user.getId());
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

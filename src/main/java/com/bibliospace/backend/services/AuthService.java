package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.request.ChangePasswordRequest;
import com.bibliospace.backend.dtos.request.LoginRequest;
import com.bibliospace.backend.dtos.request.ResetPasswordRequest;
import com.bibliospace.backend.dtos.request.RegisterRequest;
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

    // Dirección del frontend, para armar el enlace del correo de recuperación
    @Value("${app.frontend-url:http://localhost:8443}")
    private String frontendUrl;

    @Value("${app.reset-token-minutes:60}")
    private int resetTokenMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Ya existe una cuenta registrada con ese email.");
        }

        // Por defecto, los registros desde el frontend serán de ESTUDIANTE
        User user = User.builder()
                .nombre(request.getNombre().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .rol(Role.ROLE_ESTUDIANTE)
                .build();

        userRepository.save(user);
        emailService.sendWelcome(user.getEmail(), user.getNombre());

        // Hacemos login automático después del registro
        return login(new LoginRequest(email, request.getPassword()));
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

        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .nombre(user.getNombre())
                .email(user.getEmail())
                .rol(user.getRol().name())
                .build();
    }

    public UserResponse me() {
        return UserService.toResponse(currentUserService.get(), null);
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
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
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

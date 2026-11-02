package com.bibliospace.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    // Clave para firmar los tokens: se toma de JWT_SECRET y nunca se guarda en el código
    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private int jwtExpirationMs;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            // Sin JWT_SECRET se genera una clave al azar: sirve para desarrollo, pero las
            // sesiones se cierran cada vez que se reinicia el backend
            byte[] aleatoria = new byte[64];
            new SecureRandom().nextBytes(aleatoria);
            signingKey = Keys.hmacShaKeyFor(aleatoria);
            log.warn("JWT_SECRET no está definida: se generó una clave temporal. Las sesiones se cerrarán al reiniciar el backend.");
            return;
        }
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres.");
        }
        signingKey = Keys.hmacShaKeyFor(bytes);
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getEmailFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Token inválido, expirado, etc.
            return false;
        }
    }
}
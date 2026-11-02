package com.bibliospace.backend.services;

import com.bibliospace.backend.exceptions.ApiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

// Reglas para las contraseñas nuevas (registro, cambio, recuperación y las que asigna el administrador).
// Siguen la recomendación del NIST (SP 800-63B): longitud mínima y rechazo de contraseñas fáciles de adivinar,
// en lugar de obligar a usar símbolos. Las contraseñas que ya existen no se revisan.
@Component
public class PasswordPolicy {

    public static final int MIN_LONGITUD = 8;

    private static final String COMUN = "Esa contraseña es muy común o fácil de adivinar. "
            + "Usa una frase o varias palabras que no tengan que ver contigo.";

    // Palabras que, seguidas solo de números o símbolos, dan contraseñas predecibles (ej: "Espacia2026!")
    private static final Set<String> PALABRAS_BASE = Set.of(
            "password", "passw0rd", "contrasena", "micontrasena", "clave", "qwerty", "qwertyuiop", "asdfghjk",
            "admin", "administrador", "usuario", "user", "welcome", "bienvenido", "bienvenida", "iloveyou",
            "teamo", "tequiero", "espacia", "bibliospace", "biblioteca", "universidad", "estudiante", "amigo",
            "colombia", "medellin", "bogota", "abcdefgh", "abc", "abcd", "letmein");

    // Secuencias de teclado, letras y números, hacia adelante y hacia atrás
    private static final String[] SECUENCIAS = {
            "01234567890123456789", "abcdefghijklmnopqrstuvwxyz", "qwertyuiop", "asdfghjklñ", "zxcvbnm",
            "98765432109876543210", "zyxwvutsrqponmlkjihgfedcba", "poiuytrewq", "ñlkjhgfdsa", "mnbvcxz"};

    private final Set<String> comunes = cargar("seguridad/contrasenas-comunes.txt");

    // Lanza 400 con un mensaje para mostrar si la contraseña no se acepta
    public void validar(String password, String email, String nombre) {
        if (password == null || password.length() < MIN_LONGITUD) {
            throw ApiException.badRequest("La contraseña debe tener al menos " + MIN_LONGITUD + " caracteres.");
        }
        String p = simplificar(password);
        String sinFinal = p.replaceAll("[\\d\\W_]+$", "");

        if (comunes.contains(p) || (sinFinal.length() >= 3 && (comunes.contains(sinFinal) || PALABRAS_BASE.contains(sinFinal)))
                || PALABRAS_BASE.contains(p) || p.chars().distinct().count() <= 2 || esSecuencia(p)) {
            throw ApiException.badRequest(COMUN);
        }
        if (contieneDatoPersonal(p, email, nombre)) {
            throw ApiException.badRequest("La contraseña no puede contener tu correo ni tu nombre.");
        }
    }

    private static boolean esSecuencia(String p) {
        for (String s : SECUENCIAS) {
            if (s.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contieneDatoPersonal(String p, String email, String nombre) {
        if (email != null) {
            String usuario = simplificar(email.contains("@") ? email.substring(0, email.indexOf('@')) : email);
            if (usuario.length() >= 4 && p.contains(usuario)) {
                return true;
            }
            for (String parte : usuario.split("[._+\\-\\d]+")) {
                if (parte.length() >= 4 && p.contains(parte)) {
                    return true;
                }
            }
        }
        if (nombre != null) {
            for (String parte : simplificar(nombre).split("\\s+")) {
                if (parte.length() >= 4 && p.contains(parte)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Minúsculas y sin tildes, para comparar "Contraseña" con "contrasena"
    private static String simplificar(String texto) {
        String sinTildes = Normalizer.normalize(texto.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes;
    }

    private static Set<String> cargar(String recurso) {
        Set<String> lista = new HashSet<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new ClassPathResource(recurso).getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = in.readLine()) != null) {
                if (!linea.isBlank() && !linea.startsWith("#")) {
                    lista.add(simplificar(linea));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la lista de contraseñas comunes", e);
        }
        return lista;
    }
}

package com.bibliospace.backend.services;

import com.bibliospace.backend.exceptions.ApiException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// Límites sencillos en memoria (se reinician al reiniciar el backend):
//  - inicio de sesión: tras 5 contraseñas incorrectas en 15 minutos, la cuenta se bloquea 15 minutos
//  - recuperar contraseña: máximo 3 solicitudes por correo cada hora
@Service
public class RateLimitService {

    private static final int MAX_FALLOS = 5;
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    private static final Duration BLOQUEO = Duration.ofMinutes(15);
    private static final int MAX_RECUPERACIONES = 3;
    private static final Duration VENTANA_RECUPERACION = Duration.ofHours(1);
    private static final int MAX_ENTRADAS = 5000;

    private static final class Intentos {
        int fallos;
        Instant desde = Instant.now();
        Instant bloqueadoHasta;
    }

    private final Map<String, Intentos> login = new HashMap<>();
    private final Map<String, Deque<Instant>> recuperaciones = new HashMap<>();

    // Lanza 429 si la cuenta está bloqueada
    public synchronized void checkLogin(String clave) {
        Intentos i = login.get(clave);
        if (i == null || i.bloqueadoHasta == null) {
            return;
        }
        Instant ahora = Instant.now();
        if (ahora.isBefore(i.bloqueadoHasta)) {
            long minutos = Math.max(1, Duration.between(ahora, i.bloqueadoHasta).toMinutes() + 1);
            throw ApiException.tooManyRequests("Demasiados intentos fallidos. Intenta de nuevo en "
                    + minutos + (minutos == 1 ? " minuto." : " minutos."));
        }
        login.remove(clave);
    }

    public synchronized void loginFailed(String clave) {
        Instant ahora = Instant.now();
        Intentos i = login.computeIfAbsent(clave, k -> new Intentos());
        if (Duration.between(i.desde, ahora).compareTo(VENTANA_LOGIN) > 0) {
            i.fallos = 0;
            i.desde = ahora;
        }
        i.fallos++;
        if (i.fallos >= MAX_FALLOS) {
            i.bloqueadoHasta = ahora.plus(BLOQUEO);
        }
        limpiar(ahora);
    }

    public synchronized void loginOk(String clave) {
        login.remove(clave);
    }

    // true si se permite la solicitud; false si ya superó el límite
    public synchronized boolean allowPasswordReset(String clave) {
        Instant ahora = Instant.now();
        Deque<Instant> cola = recuperaciones.computeIfAbsent(clave, k -> new ArrayDeque<>());
        while (!cola.isEmpty() && Duration.between(cola.peekFirst(), ahora).compareTo(VENTANA_RECUPERACION) > 0) {
            cola.pollFirst();
        }
        if (cola.size() >= MAX_RECUPERACIONES) {
            return false;
        }
        cola.addLast(ahora);
        return true;
    }

    // Evita que los mapas crezcan sin límite
    private void limpiar(Instant ahora) {
        if (login.size() > MAX_ENTRADAS) {
            login.values().removeIf(i -> i.bloqueadoHasta == null
                    ? Duration.between(i.desde, ahora).compareTo(VENTANA_LOGIN) > 0
                    : ahora.isAfter(i.bloqueadoHasta));
        }
        if (recuperaciones.size() > MAX_ENTRADAS) {
            recuperaciones.values().removeIf(c -> c.isEmpty()
                    || Duration.between(c.peekLast(), ahora).compareTo(VENTANA_RECUPERACION) > 0);
        }
    }
}

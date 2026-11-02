package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.request.BookingRulesRequest;
import com.bibliospace.backend.entities.BookingRules;
import com.bibliospace.backend.exceptions.ApiException;
import com.bibliospace.backend.repositories.BookingRulesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// Reglas de reserva vigentes. Se guardan en la base de datos y se mantienen en memoria,
// porque se consultan en cada reserva; al editarlas desde el panel se aplican de inmediato.
@Service
@RequiredArgsConstructor
public class BookingRulesService {

    private final BookingRulesRepository repository;
    private final CurrentUserService currentUserService;

    private volatile BookingRules cache;

    // Un dominio como "amigo.edu.co": etiquetas de letras, números y guiones separadas por puntos
    private static final Pattern DOMINIO = Pattern.compile("^(?=.{3,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");

    public BookingRules get() {
        BookingRules actual = cache;
        if (actual == null) {
            actual = cargar();
        }
        return actual;
    }

    private synchronized BookingRules cargar() {
        if (cache == null) {
            BookingRules reglas = repository.findById(BookingRules.ID).orElseGet(() -> repository.save(BookingRules.defaults()));
            // Instalaciones anteriores a los dominios institucionales: se completan con el valor por defecto
            if (reglas.getDominiosInstitucionales() == null) {
                reglas.setDominiosInstitucionales(BookingRules.DOMINIOS_POR_DEFECTO);
                reglas = repository.save(reglas);
            }
            cache = reglas;
        }
        return cache;
    }

    @Transactional
    public synchronized BookingRules update(BookingRulesRequest r) {
        if (r.getHoraCierre() <= r.getHoraApertura()) {
            throw ApiException.badRequest("La hora de cierre debe ser posterior a la hora de apertura.");
        }
        if (r.getMaxHorasReserva() > r.getHoraCierre() - r.getHoraApertura()) {
            throw ApiException.badRequest("La duración máxima de una reserva no puede superar las horas de apertura ("
                    + (r.getHoraCierre() - r.getHoraApertura()) + " h).");
        }
        if (r.getMaxEquiposDia() == 0 && r.getMaxSalasDia() == 0) {
            throw ApiException.badRequest("Debe permitirse reservar al menos un equipo o una sala por día.");
        }
        String dominios = r.getDominiosInstitucionales() == null ? null : normalizarDominios(r.getDominiosInstitucionales());
        BookingRules reglas = repository.findById(BookingRules.ID).orElseGet(BookingRules::defaults);
        if (dominios != null) {
            reglas.setDominiosInstitucionales(dominios);
        }
        reglas.setHoraApertura(r.getHoraApertura());
        reglas.setHoraCierre(r.getHoraCierre());
        reglas.setMaxHorasReserva(r.getMaxHorasReserva());
        reglas.setMaxReservasActivas(r.getMaxReservasActivas());
        reglas.setMaxEquiposDia(r.getMaxEquiposDia());
        reglas.setMaxSalasDia(r.getMaxSalasDia());
        reglas.setHorasCancelacion(r.getHorasCancelacion());
        reglas.setMinutosNoAsistencia(r.getMinutosNoAsistencia());
        reglas.setDiasAnticipacion(r.getDiasAnticipacion());
        reglas.setActualizadoEn(LocalDateTime.now());
        reglas.setActualizadoPor(currentUserService.get().getEmail());
        cache = repository.save(reglas);
        return cache;
    }

    // Lista de dominios institucionales vigentes, en minúsculas
    public List<String> dominiosInstitucionales() {
        String valor = get().getDominiosInstitucionales();
        if (valor == null || valor.isBlank()) {
            return List.of();
        }
        return Arrays.stream(valor.split(",")).map(String::trim).filter(d -> !d.isEmpty()).toList();
    }

    // true si el correo pertenece a un dominio institucional o a uno de sus subdominios
    // (ej: con "amigo.edu.co", también cuenta "ana@est.amigo.edu.co")
    public boolean esInstitucional(String email) {
        if (email == null) {
            return false;
        }
        int arroba = email.lastIndexOf('@');
        if (arroba < 0) {
            return false;
        }
        String dominio = email.substring(arroba + 1).trim().toLowerCase(Locale.ROOT);
        return dominiosInstitucionales().stream()
                .anyMatch(d -> dominio.equals(d) || dominio.endsWith("." + d));
    }

    // Acepta "amigo.edu.co, @otra.edu.co" (comas, espacios o saltos de línea) y devuelve "amigo.edu.co,otra.edu.co"
    static String normalizarDominios(String texto) {
        Set<String> dominios = new LinkedHashSet<>();
        for (String parte : texto.split("[,;\\s]+")) {
            String d = parte.trim().toLowerCase(Locale.ROOT);
            if (d.startsWith("@")) {
                d = d.substring(1);
            }
            if (d.isEmpty()) {
                continue;
            }
            if (!DOMINIO.matcher(d).matches()) {
                throw ApiException.badRequest("\"" + parte.trim() + "\" no es un dominio válido. Escribe solo la parte después de la @, por ejemplo amigo.edu.co.");
            }
            dominios.add(d);
        }
        if (dominios.size() > 20) {
            throw ApiException.badRequest("Puedes registrar hasta 20 dominios institucionales.");
        }
        return String.join(",", dominios);
    }
}

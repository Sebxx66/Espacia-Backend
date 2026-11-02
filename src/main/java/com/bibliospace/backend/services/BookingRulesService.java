package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.request.BookingRulesRequest;
import com.bibliospace.backend.entities.BookingRules;
import com.bibliospace.backend.exceptions.ApiException;
import com.bibliospace.backend.repositories.BookingRulesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Reglas de reserva vigentes. Se guardan en la base de datos y se mantienen en memoria,
// porque se consultan en cada reserva; al editarlas desde el panel se aplican de inmediato.
@Service
@RequiredArgsConstructor
public class BookingRulesService {

    private final BookingRulesRepository repository;
    private final CurrentUserService currentUserService;

    private volatile BookingRules cache;

    public BookingRules get() {
        BookingRules actual = cache;
        if (actual == null) {
            actual = cargar();
        }
        return actual;
    }

    private synchronized BookingRules cargar() {
        if (cache == null) {
            cache = repository.findById(BookingRules.ID).orElseGet(() -> repository.save(BookingRules.defaults()));
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
        BookingRules reglas = repository.findById(BookingRules.ID).orElseGet(BookingRules::defaults);
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
}

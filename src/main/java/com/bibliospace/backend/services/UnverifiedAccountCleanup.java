package com.bibliospace.backend.services;

import com.bibliospace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Una vez al día borra las cuentas que se registraron y nunca confirmaron el correo.
// Así un correo mal escrito, o usado por otra persona, no queda ocupado para siempre.
@Slf4j
@Service
@RequiredArgsConstructor
public class UnverifiedAccountCleanup {

    private final UserRepository userRepository;

    @Value("${app.unverified-account-days:7}")
    private int dias;

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void limpiar() {
        try {
            int borradas = userRepository.deleteUnverifiedCreatedBefore(LocalDateTime.now().minusDays(dias));
            if (borradas > 0) {
                log.info("Se borraron {} cuentas sin confirmar de más de {} días", borradas, dias);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudieron borrar las cuentas sin confirmar: {}", e.getMessage());
        }
    }
}

package com.espacia.backend.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Con el modo demostración activo, todas las noches se borra lo que hicieron los visitantes
// con las cuentas de demostración y se vuelven a crear sus datos de ejemplo
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoResetJob {

    private final DemoDataService demoDataService;

    @Scheduled(cron = "${app.demo.reset-cron:0 0 3 * * *}")
    public void reiniciar() {
        demoDataService.reiniciar();
    }
}

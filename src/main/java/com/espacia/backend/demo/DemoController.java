package com.espacia.backend.demo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Público: le dice al frontend si debe mostrar los botones de "Probar la demo" y con qué cuentas
@RestController
@RequestMapping("/api/v1/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoDataService demoDataService;

    @Value("${app.demo.enabled:false}")
    private boolean modoDemo;

    @Value("${app.demo.reset-time:3:00 a. m.}")
    private String horaReinicio;

    @GetMapping
    public DemoInfo info() {
        if (!modoDemo) {
            return new DemoInfo(false, null, List.of());
        }
        String password = demoDataService.password();
        List<DemoInfo.Cuenta> cuentas = demoDataService.cuentasVisibles().stream()
                .map(c -> new DemoInfo.Cuenta(c.rol().name(), c.nombre(), c.email(), password))
                .toList();
        return new DemoInfo(true, horaReinicio, cuentas);
    }

    public record DemoInfo(boolean enabled, String resetTime, List<Cuenta> accounts) {
        public record Cuenta(String rol, String nombre, String email, String password) {
        }
    }
}

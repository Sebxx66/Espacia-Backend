package com.bibliospace.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Documentación interactiva de la API: http://localhost:8080/swagger-ui.html
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI espaciaOpenApi(@Value("${app.version:dev}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("Espacia API")
                        .version(version)
                        .description("API REST para reservar salas de estudio, equipos y talleres de la biblioteca. "
                                + "Inicia sesión con POST /api/v1/auth/login, copia el token de la respuesta "
                                + "y pégalo en el botón Authorize para probar las demás rutas."))
                .components(new Components().addSecuritySchemes("token", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("token"));
    }
}

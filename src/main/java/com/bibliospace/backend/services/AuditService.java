package com.bibliospace.backend.services;

import com.bibliospace.backend.entities.AuditLog;
import com.bibliospace.backend.entities.User;
import com.bibliospace.backend.repositories.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

// Anota en el registro de auditoría las acciones del panel. Se llama dentro de la misma transacción
// que la acción: si la acción falla, tampoco queda registrada.
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final CurrentUserService currentUserService;
    private final ClientIpResolver clientIpResolver;

    public void registrar(String accion, String detalle) {
        User quien = null;
        try {
            quien = currentUserService.get();
        } catch (RuntimeException e) {
            // Acción del sistema, sin usuario en sesión
        }
        String texto = detalle == null ? "" : detalle.trim();
        repository.save(AuditLog.builder()
                .usuarioNombre(quien == null ? "Sistema" : quien.getNombre())
                .usuarioEmail(quien == null ? null : quien.getEmail())
                .usuarioRol(quien == null ? null : quien.getRol().name())
                .accion(accion)
                .detalle(texto.length() > 500 ? texto.substring(0, 497) + "..." : texto)
                .ip(quien == null ? null : clientIpResolver.actual())
                .build());
    }

    public static String rol(com.bibliospace.backend.entities.Role rol) {
        return switch (rol) {
            case ROLE_ADMIN -> "administrador";
            case ROLE_FUNCIONARIO -> "funcionario";
            default -> "estudiante";
        };
    }

    public static String persona(User u) {
        return u.getNombre() + " (" + u.getEmail() + ")";
    }

    public Page<AuditLog> listar(String texto, int pagina, int tamano) {
        PageRequest page = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100));
        if (texto == null || texto.isBlank()) {
            return repository.findAllByOrderByFechaDescIdDesc(page);
        }
        return repository.buscar(texto.trim(), page);
    }
}

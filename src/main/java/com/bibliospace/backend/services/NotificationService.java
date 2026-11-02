package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.response.NotificationResponse;
import com.bibliospace.backend.entities.Notification;
import com.bibliospace.backend.entities.User;
import com.bibliospace.backend.exceptions.ApiException;
import com.bibliospace.backend.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;
    private final EmailService emailService;

    // Estas notificaciones también se envían por correo (las demás solo se ven dentro de la app)
    private static final Set<String> TIPOS_CON_CORREO = Set.of("CONFIRMACION", "CANCELACION", "RECHAZADA", "NO_ASISTIDA");

    public void notify(User usuario, String tipo, String mensaje) {
        notificationRepository.save(Notification.builder()
                .usuario(usuario)
                .tipo(tipo)
                .mensaje(mensaje)
                .build());

        if (TIPOS_CON_CORREO.contains(tipo)) {
            // Se toman los datos ahora: el envío es asíncrono y el usuario puede ser un proxy perezoso
            String email = usuario.getEmail();
            String nombre = usuario.getNombre();
            Runnable envio = () -> emailService.sendReservationNotice(email, nombre, tipo, mensaje);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                // Solo se envía si la transacción de la reserva termina bien
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        envio.run();
                    }
                });
            } else {
                envio.run();
            }
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> mine() {
        return notificationRepository.findTop50ByUsuarioIdOrderByIdDesc(currentUserService.get().getId())
                .stream()
                .map(n -> new NotificationResponse(n.getId(), n.getMensaje(), n.getTipo(), n.getLeida(), n.getCreadaEn()))
                .toList();
    }

    @Transactional
    public void markRead(Long id) {
        Long userId = currentUserService.get().getId();
        Notification notification = notificationRepository.findById(id)
                .filter(n -> n.getUsuario().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Notificación no encontrada."));
        notification.setLeida(true);
    }

    @Transactional
    public void markAllRead() {
        notificationRepository.findByUsuarioIdAndLeidaFalse(currentUserService.get().getId())
                .forEach(n -> n.setLeida(true));
    }
}

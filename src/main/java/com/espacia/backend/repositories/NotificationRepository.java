package com.espacia.backend.repositories;

import com.espacia.backend.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Datos de las cuentas de demostración (se borran en el reinicio nocturno)
    long deleteByUsuarioDemoTrue();


    List<Notification> findTop50ByUsuarioIdOrderByIdDesc(Long usuarioId);

    List<Notification> findByUsuarioIdAndLeidaFalse(Long usuarioId);
}

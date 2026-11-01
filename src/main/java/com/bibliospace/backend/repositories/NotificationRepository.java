package com.bibliospace.backend.repositories;

import com.bibliospace.backend.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUsuarioIdOrderByIdDesc(Long usuarioId);

    List<Notification> findByUsuarioIdAndLeidaFalse(Long usuarioId);
}

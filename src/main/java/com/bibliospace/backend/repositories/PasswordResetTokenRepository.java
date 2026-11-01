package com.bibliospace.backend.repositories;

import com.bibliospace.backend.entities.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    // Deja sin uso todos los enlaces pendientes de un usuario (al pedir uno nuevo o al cambiar la clave)
    @Modifying
    @Query("update PasswordResetToken t set t.usado = true where t.usuario.id = :userId and t.usado = false")
    void invalidateAllForUser(@Param("userId") Long userId);
}

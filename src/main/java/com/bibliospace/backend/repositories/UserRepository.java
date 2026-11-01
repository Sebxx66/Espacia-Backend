package com.bibliospace.backend.repositories;

import com.bibliospace.backend.entities.Role;
import com.bibliospace.backend.entities.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRol(Role rol);

    long countByRolAndActivoTrue(Role rol);

    List<User> findAllByOrderByNombreAsc();

    // Bloquea al usuario hasta que termine la transacción, para que dos reservas suyas
    // enviadas al mismo tiempo no se salten el límite de reservas activas
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}

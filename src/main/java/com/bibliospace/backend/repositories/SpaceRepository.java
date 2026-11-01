package com.bibliospace.backend.repositories;

import com.bibliospace.backend.entities.Space;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Long> {

    // Encuentra todos los espacios por su tipo (Ej: "SALA_ESTUDIO")
    List<Space> findByTipo(String tipo);

    // Encuentra todos los espacios por su estado (Ej: "ACTIVO")
    List<Space> findByEstado(String estado);

    // Bloquea la fila del espacio hasta que termine la transacción, para que dos
    // reservas simultáneas del mismo espacio se validen una después de la otra
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Space s where s.id = :id")
    Optional<Space> findByIdForUpdate(@Param("id") Long id);
}

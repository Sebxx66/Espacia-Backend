package com.espacia.backend.repositories;

import com.espacia.backend.entities.Incident;
import com.espacia.backend.entities.IncidentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    // Datos de las cuentas de demostración (se borran en el reinicio nocturno)
    long deleteByReportadoPorDemoTrue();


    @EntityGraph(attributePaths = {"espacio", "reportadoPor"})
    List<Incident> findAllByOrderByIdDesc();

    boolean existsByEspacioId(Long espacioId);

    long countByEstado(IncidentStatus estado);
}

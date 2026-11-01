package com.bibliospace.backend.repositories;

import com.bibliospace.backend.entities.Reservation;
import com.bibliospace.backend.entities.ReservationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // Lista el historial de reservas de un estudiante específico
    List<Reservation> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {"usuario", "espacio"})
    List<Reservation> findByUsuarioIdOrderByFechaDescHoraInicioDesc(Long usuarioId);

    // Útil para verificar qué bloques horarios ya están ocupados en un espacio y fecha
    List<Reservation> findByEspacioIdAndFecha(Long espacioId, LocalDate fecha);

    @EntityGraph(attributePaths = {"usuario", "espacio"})
    List<Reservation> findByEspacioIdAndFechaGreaterThanEqual(Long espacioId, LocalDate fecha);

    @EntityGraph(attributePaths = {"usuario", "espacio"})
    List<Reservation> findByFecha(LocalDate fecha);

    @EntityGraph(attributePaths = {"usuario", "espacio"})
    List<Reservation> findByFechaBetween(LocalDate desde, LocalDate hasta);

    // Para que el Funcionario vea todas las reservas en un estado (Ej: "PENDIENTE")
    List<Reservation> findByEstado(ReservationStatus estado);

    @EntityGraph(attributePaths = {"usuario", "espacio"})
    List<Reservation> findAllByOrderByFechaDescHoraInicioDesc();

    @EntityGraph(attributePaths = {"usuario", "espacio"})
    List<Reservation> findTop6ByOrderByIdDesc();

    // Reservas confirmadas a las que aún no se les envió el recordatorio
    @EntityGraph(attributePaths = {"usuario", "espacio"})
    List<Reservation> findByEstadoAndRecordatorioEnviadoFalseAndFechaBetween(ReservationStatus estado, LocalDate desde, LocalDate hasta);

    boolean existsByEspacioId(Long espacioId);

    // [usuarioId, cantidad] para mostrar cuántas reservas tiene cada usuario
    @Query("select r.usuario.id, count(r) from Reservation r group by r.usuario.id")
    List<Object[]> countGroupedByUsuario();
}

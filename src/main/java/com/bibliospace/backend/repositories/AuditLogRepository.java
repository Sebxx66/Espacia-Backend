package com.bibliospace.backend.repositories;

import com.bibliospace.backend.entities.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByFechaDescIdDesc(Pageable pageable);

    @Query("""
            select a from AuditLog a
            where lower(a.detalle) like lower(concat('%', :texto, '%'))
               or lower(a.usuarioEmail) like lower(concat('%', :texto, '%'))
               or lower(a.usuarioNombre) like lower(concat('%', :texto, '%'))
               or lower(a.accion) like lower(concat('%', :texto, '%'))
            order by a.fecha desc, a.id desc""")
    Page<AuditLog> buscar(@Param("texto") String texto, Pageable pageable);
}

package com.taqueria.repository;

import com.taqueria.model.*;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    @Query("select a from Auditoria a where a.creadoEn >= :desde and a.creadoEn < :hasta " +
           "and (:q is null or lower(a.accion) like lower(concat('%', cast(:q as string), '%')) " +
           "or lower(coalesce(a.usuarioNombre, '')) like lower(concat('%', cast(:q as string), '%')) " +
           "or lower(coalesce(a.detalle, '')) like lower(concat('%', cast(:q as string), '%'))) order by a.creadoEn desc")
    Page<Auditoria> buscar(String q, Instant desde, Instant hasta, Pageable pageable);
}

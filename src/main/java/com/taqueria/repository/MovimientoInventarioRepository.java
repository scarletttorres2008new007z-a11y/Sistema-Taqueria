package com.taqueria.repository;

import com.taqueria.model.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    @Query("select m from MovimientoInventario m where (:itemId is null or m.item.id = :itemId) " +
           "and (:tipo is null or m.tipo = :tipo) and m.creadoEn >= :desde and m.creadoEn < :hasta order by m.creadoEn desc, m.id desc")
    Page<MovimientoInventario> buscar(Long itemId, TipoMovimientoInventario tipo, Instant desde, Instant hasta, Pageable pageable);

    List<MovimientoInventario> findByReferenciaTipoAndReferenciaId(String referenciaTipo, Long referenciaId);

    List<MovimientoInventario> findByCreadoEnBetweenOrderByCreadoEnAsc(Instant desde, Instant hasta);
}

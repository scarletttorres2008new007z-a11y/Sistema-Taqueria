package com.taqueria.repository;

import com.taqueria.model.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CajaRepository extends JpaRepository<Caja, Long> {

    Optional<Caja> findFirstByEstado(EstadoCaja estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Caja c where c.estado = com.taqueria.model.EstadoCaja.ABIERTA")
    Optional<Caja> bloquearAbierta();

    Page<Caja> findByEstadoOrderByAbiertaEnDesc(EstadoCaja estado, Pageable pageable);

    List<Caja> findByCerradaEnGreaterThanEqualAndCerradaEnLessThanOrderByCerradaEnAsc(Instant desde, Instant hasta);
}

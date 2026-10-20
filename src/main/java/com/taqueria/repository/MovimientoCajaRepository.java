package com.taqueria.repository;

import com.taqueria.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    List<MovimientoCaja> findByCajaIdOrderByCreadoEnAscIdAsc(Long cajaId);

    boolean existsByReferenciaTipoAndReferenciaIdAndTipo(String referenciaTipo, Long referenciaId, TipoMovimientoCaja tipo);
}

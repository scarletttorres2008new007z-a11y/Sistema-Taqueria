package com.taqueria.repository;

import com.taqueria.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findByVentaIdOrderByCreadoEnAsc(Long ventaId);

    Optional<Pago> findByClaveIdempotencia(String clave);

    List<Pago> findByCreadoEnGreaterThanEqualAndCreadoEnLessThan(Instant desde, Instant hasta);
}

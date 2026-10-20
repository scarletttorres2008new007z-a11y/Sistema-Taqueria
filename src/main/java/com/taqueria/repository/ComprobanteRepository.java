package com.taqueria.repository;

import com.taqueria.model.*;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    Optional<Comprobante> findByVentaId(Long ventaId);

    Optional<Comprobante> findByNumero(String numero);

    @Query("select c from Comprobante c where (:numero is null or upper(c.numero) like upper(concat('%', cast(:numero as string), '%'))) " +
           "and (:cliente is null or lower(coalesce(c.clienteNombre, '')) like lower(concat('%', cast(:cliente as string), '%'))) " +
           "and (:estado is null or c.estado = :estado) and c.fechaEmision >= :desde and c.fechaEmision < :hasta order by c.fechaEmision desc")
    Page<Comprobante> buscar(String numero, String cliente, EstadoComprobante estado, Instant desde, Instant hasta, Pageable pageable);

    @Modifying
    @Query("update Comprobante c set c.impresiones = c.impresiones + 1, c.ultimaImpresion = :ahora where c.id = :id")
    int registrarImpresion(Long id, Instant ahora);
}

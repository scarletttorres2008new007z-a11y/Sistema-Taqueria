package com.taqueria.repository;

import com.taqueria.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PedidoEstadoRepository extends JpaRepository<PedidoEstado, Long> {

    List<PedidoEstado> findByPedidoIdOrderByCreadoEnAscIdAsc(Long pedidoId);
}

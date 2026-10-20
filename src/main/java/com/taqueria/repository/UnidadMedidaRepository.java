package com.taqueria.repository;

import com.taqueria.model.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Long> {

    List<UnidadMedida> findAllByOrderByTipoAscFactorAsc();

    Optional<UnidadMedida> findByCodigo(String codigo);
}

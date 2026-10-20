package com.taqueria.repository;

import com.taqueria.model.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RecetaRepository extends JpaRepository<Receta, Long> {

    Optional<Receta> findByProductoId(Long productoId);

    List<Receta> findByProductoIdIn(Collection<Long> productoIds);
}

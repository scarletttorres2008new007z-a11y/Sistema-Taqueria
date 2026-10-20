package com.taqueria.repository;

import com.taqueria.model.*;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RolRepository extends JpaRepository<Rol, Long> {

    Optional<Rol> findByCodigo(String codigo);
}

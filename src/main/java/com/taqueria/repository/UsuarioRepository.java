package com.taqueria.repository;

import com.taqueria.model.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("select u from Usuario u where lower(u.username) = lower(:username)")
    Optional<Usuario> buscarPorUsername(String username);

    List<Usuario> findAllByOrderByNombreAsc();

    @Query("select count(u) from Usuario u where u.activo = true and u.rol.codigo = 'ADMIN'")
    long contarAdministradoresActivos();
}

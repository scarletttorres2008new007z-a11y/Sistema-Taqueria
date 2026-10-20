package com.taqueria.model;

import java.util.Set;
import java.util.TreeSet;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nombre;

    private String descripcion;

    /** Los cargos del sistema no se pueden eliminar. */
    private boolean sistema;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rol_permisos", joinColumns = @JoinColumn(name = "rol_id"))
    @Column(name = "permiso")
    private Set<String> permisos = new TreeSet<>();

    public boolean isAdministrador() {
        return "ADMIN".equals(codigo);
    }
}

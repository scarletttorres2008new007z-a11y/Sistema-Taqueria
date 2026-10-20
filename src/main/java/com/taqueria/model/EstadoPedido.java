package com.taqueria.model;

import java.util.EnumSet;
import java.util.Set;

/** Estados de un pedido y transiciones permitidas. */
public enum EstadoPedido {
    PENDIENTE, EN_PREPARACION, LISTO, ENTREGADO, CANCELADO;

    public Set<EstadoPedido> siguientes() {
        return switch (this) {
            case PENDIENTE -> EnumSet.of(EN_PREPARACION, LISTO, CANCELADO);
            case EN_PREPARACION -> EnumSet.of(LISTO, PENDIENTE, CANCELADO);
            case LISTO -> EnumSet.of(ENTREGADO, EN_PREPARACION, CANCELADO);
            case ENTREGADO, CANCELADO -> EnumSet.noneOf(EstadoPedido.class);
        };
    }

    public boolean puedeCambiarA(EstadoPedido nuevo) {
        return siguientes().contains(nuevo);
    }
}

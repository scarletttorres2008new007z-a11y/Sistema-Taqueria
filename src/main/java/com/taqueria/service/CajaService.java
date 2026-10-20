package com.taqueria.service;

import com.taqueria.dto.Dtos.AperturaCajaRequest;
import com.taqueria.dto.Dtos.CierreCajaRequest;
import com.taqueria.dto.Dtos.MovimientoCajaRequest;
import com.taqueria.dto.Vistas.CajaResumen;
import com.taqueria.dto.Vistas.MovimientoCajaVista;
import com.taqueria.model.*;
import com.taqueria.repository.CajaRepository;
import com.taqueria.repository.MovimientoCajaRepository;
import com.taqueria.security.UsuarioActual;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Control de caja. Solo puede haber una caja abierta. Las ventas pendientes de pago no
 * generan movimientos; los pagos con tarjeta o transferencia se registran aparte del efectivo.
 */
@Service
public class CajaService {

    private final CajaRepository cajas;
    private final MovimientoCajaRepository movimientos;
    private final UsuarioService usuarios;
    private final AuditoriaService auditoria;

    public CajaService(CajaRepository cajas, MovimientoCajaRepository movimientos, UsuarioService usuarios,
                       AuditoriaService auditoria) {
        this.cajas = cajas;
        this.movimientos = movimientos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    public Optional<Caja> actual() {
        return cajas.findFirstByEstado(EstadoCaja.ABIERTA);
    }

    /** Devuelve la caja abierta bloqueada hasta el final de la transaccion. */
    public Caja requerirAbierta() {
        return cajas.bloquearAbierta()
                .orElseThrow(() -> ApiException.conflicto("La caja está cerrada. Abra la caja antes de registrar pagos o movimientos."));
    }

    @Transactional
    public Caja abrir(AperturaCajaRequest req) {
        if (actual().isPresent()) throw ApiException.conflicto("Ya hay una caja abierta");
        Caja c = new Caja();
        c.setUsuarioAperturaId(UsuarioActual.id());
        c.setMontoInicial(Dinero.r(req.montoInicial()));
        c.setNotas(Texto.limpio(req.notas()));
        try {
            cajas.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflicto("Ya hay una caja abierta");
        }
        auditoria.registrar("Apertura de caja", "Caja", c.getId(), "Saldo inicial $" + c.getMontoInicial());
        return c;
    }

    @Transactional
    public CajaResumen cerrar(CierreCajaRequest req) {
        Caja c = requerirAbierta();
        CajaResumen r = resumen(c);
        c.setEfectivoEsperado(r.efectivoEsperado());
        c.setEfectivoContado(Dinero.r(req.efectivoContado()));
        c.setDiferencia(c.getEfectivoContado().subtract(r.efectivoEsperado()));
        c.setUsuarioCierreId(UsuarioActual.id());
        c.setCerradaEn(Instant.now());
        c.setEstado(EstadoCaja.CERRADA);
        String notas = Texto.limpio(req.notas());
        if (notas != null) c.setNotas(c.getNotas() == null ? notas : c.getNotas() + " | Cierre: " + notas);
        cajas.save(c);
        auditoria.registrar("Cierre de caja", "Caja", c.getId(), "Esperado $" + c.getEfectivoEsperado()
                + ", contado $" + c.getEfectivoContado() + ", diferencia $" + c.getDiferencia());
        return resumen(c);
    }

    /** Entrada o retiro de efectivo autorizado (cambio inicial adicional, retiro para deposito...). */
    @Transactional
    public MovimientoCaja movimientoManual(MovimientoCajaRequest req) {
        if (req.tipo() != TipoMovimientoCaja.INGRESO && req.tipo() != TipoMovimientoCaja.RETIRO) {
            throw new ApiException("Solo se pueden registrar entradas o retiros de efectivo");
        }
        Caja c = requerirAbierta();
        BigDecimal monto = Dinero.r(req.monto());
        if (req.tipo() == TipoMovimientoCaja.RETIRO) {
            validarEfectivoDisponible(c, monto);
            monto = monto.negate();
        }
        MovimientoCaja m = registrar(c, req.tipo(), MetodoPago.EFECTIVO, monto, req.concepto().trim(), null, null);
        auditoria.registrar(req.tipo() == TipoMovimientoCaja.INGRESO ? "Entrada de efectivo" : "Retiro de efectivo",
                "Caja", c.getId(), req.concepto().trim() + ": $" + monto.abs());
        return m;
    }

    public void validarEfectivoDisponible(Caja c, BigDecimal monto) {
        BigDecimal disponible = efectivoEsperado(c);
        if (monto.compareTo(disponible) > 0) {
            throw ApiException.conflicto("No hay suficiente efectivo en caja. Disponible: $" + disponible);
        }
    }

    /** Registra un movimiento. El indice unico impide registrar dos veces la misma referencia. */
    @Transactional
    public MovimientoCaja registrar(Caja caja, TipoMovimientoCaja tipo, MetodoPago metodo, BigDecimal montoConSigno,
                                    String concepto, String referenciaTipo, Long referenciaId) {
        MovimientoCaja m = new MovimientoCaja();
        m.setCajaId(caja.getId());
        m.setTipo(tipo);
        m.setMetodo(metodo);
        m.setMonto(Dinero.r(montoConSigno));
        m.setConcepto(concepto);
        m.setReferenciaTipo(referenciaTipo);
        m.setReferenciaId(referenciaId);
        m.setUsuarioId(UsuarioActual.id());
        try {
            return movimientos.saveAndFlush(m);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflicto("Este movimiento de caja ya fue registrado");
        }
    }

    public BigDecimal efectivoEsperado(Caja c) {
        return Dinero.r(c.getMontoInicial().add(movimientos.findByCajaIdOrderByCreadoEnAscIdAsc(c.getId()).stream()
                .filter(m -> m.getMetodo() == MetodoPago.EFECTIVO)
                .map(MovimientoCaja::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    public CajaResumen resumen(Caja c) {
        List<MovimientoCaja> lista = movimientos.findByCajaIdOrderByCreadoEnAscIdAsc(c.getId());
        Map<Long, String> nombres = usuarios.nombres();
        BigDecimal vEf = BigDecimal.ZERO, vTa = BigDecimal.ZERO, vTr = BigDecimal.ZERO, dev = BigDecimal.ZERO,
                ing = BigDecimal.ZERO, ret = BigDecimal.ZERO, gas = BigDecimal.ZERO, prov = BigDecimal.ZERO,
                efectivo = c.getMontoInicial();
        int pagos = 0;
        for (MovimientoCaja m : lista) {
            switch (m.getTipo()) {
                case VENTA -> {
                    pagos++;
                    switch (m.getMetodo()) {
                        case EFECTIVO -> vEf = vEf.add(m.getMonto());
                        case TARJETA -> vTa = vTa.add(m.getMonto());
                        case TRANSFERENCIA -> vTr = vTr.add(m.getMonto());
                    }
                }
                case DEVOLUCION -> dev = dev.add(m.getMonto());
                case INGRESO -> ing = ing.add(m.getMonto());
                case RETIRO -> ret = ret.add(m.getMonto());
                case GASTO -> gas = gas.add(m.getMonto());
                case PAGO_PROVEEDOR -> prov = prov.add(m.getMonto());
            }
            if (m.getMetodo() == MetodoPago.EFECTIVO) efectivo = efectivo.add(m.getMonto());
        }
        List<MovimientoCajaVista> vistas = lista.stream().map(m -> new MovimientoCajaVista(m.getId(), m.getTipo(),
                m.getMetodo(), m.getMonto(), m.getConcepto(), m.getReferenciaTipo(), m.getReferenciaId(),
                nombres.get(m.getUsuarioId()), m.getCreadoEn())).toList();
        return new CajaResumen(c, nombres.get(c.getUsuarioAperturaId()),
                c.getUsuarioCierreId() == null ? null : nombres.get(c.getUsuarioCierreId()),
                Dinero.r(vEf), Dinero.r(vTa), Dinero.r(vTr), Dinero.r(dev), Dinero.r(ing), Dinero.r(ret), Dinero.r(gas),
                Dinero.r(prov), Dinero.r(efectivo), pagos, vistas);
    }

    public Caja obtener(Long id) {
        return cajas.findById(id).orElseThrow(() -> ApiException.noEncontrado("Caja"));
    }

    public Page<CajaResumen> cierres(int pagina, int tamano) {
        return cajas.findByEstadoOrderByAbiertaEnDesc(EstadoCaja.CERRADA,
                PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100))).map(this::resumen);
    }
}

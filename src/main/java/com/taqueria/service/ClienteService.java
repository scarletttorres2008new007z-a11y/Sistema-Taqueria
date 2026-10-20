package com.taqueria.service;

import com.taqueria.dto.Dtos.ClienteRequest;
import com.taqueria.model.Cliente;
import com.taqueria.repository.ClienteRepository;
import com.taqueria.repository.PedidoRepository;
import com.taqueria.repository.VentaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** Clientes opcionales: no es necesario registrar un cliente para vender en mostrador. */
@Service
public class ClienteService {

    private final ClienteRepository clientes;
    private final PedidoRepository pedidos;
    private final VentaRepository ventas;
    private final AuditoriaService auditoria;

    public ClienteService(ClienteRepository clientes, PedidoRepository pedidos, VentaRepository ventas,
                          AuditoriaService auditoria) {
        this.clientes = clientes;
        this.pedidos = pedidos;
        this.ventas = ventas;
        this.auditoria = auditoria;
    }

    public Page<Cliente> buscar(String q, Boolean activo, int pagina, int tamano) {
        return clientes.buscar(Texto.limpio(q), activo, PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamano, 1), 100)));
    }

    public Cliente obtener(Long id) {
        return clientes.findById(id).orElseThrow(() -> ApiException.noEncontrado("Cliente"));
    }

    @Transactional
    public Cliente guardar(Long id, ClienteRequest req) {
        Cliente c = id == null ? new Cliente() : obtener(id);
        c.setNombre(req.nombre().trim());
        c.setTelefono(Texto.limpio(req.telefono()));
        c.setDireccion(Texto.limpio(req.direccion()));
        c.setCorreo(Texto.limpio(req.correo()));
        c.setDocumento(Texto.limpio(req.documento()));
        c.setNrc(Texto.limpio(req.nrc()));
        c.setNotas(Texto.limpio(req.notas()));
        c.setActivo(req.activo());
        clientes.save(c);
        auditoria.registrar(id == null ? "Cliente registrado" : "Cliente modificado", "Cliente", c.getId(), c.getNombre());
        return c;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> historial(Long id) {
        Cliente c = obtener(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cliente", c);
        m.put("pedidos", pedidos.findByClienteIdOrderByCreadoEnDesc(id).stream().map(p -> Map.of(
                "id", p.getId(), "numero", p.getNumero(), "fecha", p.getCreadoEn(), "tipo", p.getTipo(),
                "estado", p.getEstado(), "total", p.getTotal())).toList());
        m.put("ventas", ventas.findByClienteIdOrderByCreadoEnDesc(id).stream().map(v -> Map.of(
                "id", v.getId(), "numero", v.getNumero(), "fecha", v.getCreadoEn(), "estado", v.getEstado(),
                "total", v.getTotal())).toList());
        return m;
    }
}

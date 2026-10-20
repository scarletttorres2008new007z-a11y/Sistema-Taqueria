package com.taqueria.dto;

import com.taqueria.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Datos que recibe la API. Las validaciones se aplican en el servidor con @Valid. */
public final class Dtos {

    private Dtos() {
    }

    // ---------- Configuracion y usuarios ----------

    public record ConfiguracionRequest(
            @NotBlank(message = "El nombre comercial es obligatorio") @Size(max = 120) String nombreComercial,
            @Size(max = 150) String razonSocial,
            @Size(max = 30) String telefono,
            @Email(message = "Correo no válido") @Size(max = 120) String correo,
            @Size(max = 250) String direccion,
            @Size(max = 30) String nit,
            @Size(max = 30) String nrc,
            @Size(max = 150) String giro,
            @Size(max = 250) String mensajeComprobante,
            @NotNull @DecimalMin("0") @DecimalMax("99.99") BigDecimal ivaPorcentaje,
            @NotNull Integer anchoPapelMm) {
    }

    public record AdminInicialRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "Use solo letras, números, punto, guion o guion bajo") String username,
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres") String password,
            @NotBlank(message = "Escriba el código de configuración que aparece en la consola del servidor")
            @Size(max = 40) String codigoConfiguracion) {
    }

    public record UsuarioRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "Use solo letras, números, punto, guion o guion bajo") String username,
            @NotBlank @Size(max = 120) String nombre,
            @NotNull Long rolId,
            @Size(max = 30) String telefono,
            boolean activo,
            @Size(max = 100) String password) {
    }

    public record PasswordRequest(
            @NotBlank @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres") String nueva) {
    }

    public record CambioPasswordRequest(
            @NotBlank String actual,
            @NotBlank @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres") String nueva) {
    }

    public record RolPermisosRequest(@NotNull Set<String> permisos) {
    }

    // ---------- Catalogo ----------

    public record CategoriaRequest(@NotBlank @Size(max = 60) String nombre, int orden, boolean activa) {
    }

    public record ProductoRequest(
            @Size(max = 20) String codigo,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String nombre,
            @Size(max = 500) String descripcion,
            @NotNull(message = "Seleccione la categoría") Long categoriaId,
            @NotNull TipoProducto tipo,
            @NotNull(message = "El precio es obligatorio") @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
            @Digits(integer = 8, fraction = 2, message = "El precio admite 2 decimales") BigDecimal precio,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal costoEstimado,
            Long itemInventarioId,
            boolean activo,
            boolean disponible) {
    }

    public record ComplementoRequest(
            @NotBlank @Size(max = 80) String nombre,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal precioExtra,
            Long categoriaId,
            boolean activo) {
    }

    public record MesaRequest(@NotBlank @Size(max = 20) String nombre, @Min(1) @Max(50) int capacidad, boolean activa) {
    }

    // ---------- Inventario y recetas ----------

    public record ItemInventarioRequest(
            @Size(max = 20) String codigo,
            @NotBlank @Size(max = 100) String nombre,
            @NotNull TipoItem tipo,
            @NotBlank @Size(max = 40) String categoria,
            @NotNull Long unidadId,
            @DecimalMin("0") BigDecimal existenciaInicial,
            @NotNull @DecimalMin("0") BigDecimal existenciaMinima,
            @NotNull @DecimalMin("0") BigDecimal costoUnitario,
            Long proveedorId,
            LocalDate fechaVencimiento,
            @Size(max = 40) String lote,
            boolean activo) {
    }

    public record MovimientoInventarioRequest(
            @NotNull Long itemId,
            @NotNull TipoMovimientoInventario tipo,
            @NotNull @DecimalMin(value = "0", message = "La cantidad no puede ser negativa") BigDecimal cantidad,
            @DecimalMin("0") BigDecimal costoUnitario,
            @NotBlank(message = "Indique el motivo del movimiento") @Size(max = 250) String motivo,
            @Size(max = 40) String lote,
            LocalDate fechaVencimiento) {
    }

    public record RecetaDetalleRequest(
            @NotNull Long itemId,
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "La cantidad debe ser mayor que cero") BigDecimal cantidad,
            @NotNull Long unidadId) {
    }

    public record RecetaRequest(@Size(max = 500) String notas, @NotNull @Valid List<RecetaDetalleRequest> detalles) {
    }

    // ---------- Pedidos y ventas ----------

    public record ItemPedidoRequest(
            @NotNull Long productoId,
            @Min(value = 1, message = "La cantidad mínima es 1") @Max(value = 500, message = "Cantidad demasiado alta") int cantidad,
            @Size(max = 200) String observaciones,
            List<Long> complementoIds) {
    }

    public record PedidoRequest(
            @NotNull(message = "Seleccione el tipo de pedido") TipoPedido tipo,
            Long mesaId,
            Long clienteId,
            @Size(max = 150) String clienteNombre,
            @Size(max = 30) String clienteTelefono,
            @Size(max = 250) String direccionEntrega,
            @Size(max = 250) String referenciaEntrega,
            @Size(max = 300) String notas,
            @NotEmpty(message = "Agregue al menos un producto") @Valid List<ItemPedidoRequest> items) {
    }

    public record EstadoPedidoRequest(@NotNull EstadoPedido estado, @Size(max = 250) String motivo) {
    }

    public record MotivoRequest(@NotBlank(message = "Indique el motivo") @Size(max = 250) String motivo) {
    }

    public record PagoRequest(
            @NotNull(message = "Seleccione el método de pago") MetodoPago metodo,
            @NotNull @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
            @Digits(integer = 8, fraction = 2) BigDecimal monto,
            @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal montoRecibido,
            @Size(max = 60) String referencia) {
    }

    public record CobroRequest(
            @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal descuento,
            @Size(max = 250) String motivoDescuento,
            Long clienteId,
            @Size(max = 150) String clienteNombre,
            @Size(max = 30) String clienteDocumento,
            @Size(max = 250) String clienteDireccion,
            @Valid List<PagoRequest> pagos) {
    }

    public record VentaDirectaRequest(
            @NotEmpty(message = "Agregue al menos un producto") @Valid List<ItemPedidoRequest> items,
            @NotNull @Valid CobroRequest cobro) {
    }

    public record AnulacionRequest(
            @NotBlank(message = "Indique el motivo de la anulación") @Size(max = 250) String motivo,
            boolean devolverInventario) {
    }

    // ---------- Caja y gastos ----------

    public record AperturaCajaRequest(
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal montoInicial,
            @Size(max = 500) String notas) {
    }

    public record CierreCajaRequest(
            @NotNull(message = "Indique el efectivo contado") @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal efectivoContado,
            @Size(max = 500) String notas) {
    }

    public record MovimientoCajaRequest(
            @NotNull TipoMovimientoCaja tipo,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal monto,
            @NotBlank(message = "Indique el motivo") @Size(max = 250) String concepto) {
    }

    public record GastoRequest(
            @NotNull LocalDate fecha,
            @NotNull Long categoriaId,
            @NotBlank(message = "Indique el concepto") @Size(max = 250) String concepto,
            @NotNull @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
            @Digits(integer = 8, fraction = 2) BigDecimal monto,
            @NotNull MetodoPago metodoPago,
            boolean pagadoDesdeCaja,
            @Size(max = 60) String numeroComprobante) {
    }

    public record CategoriaGastoRequest(@NotBlank @Size(max = 60) String nombre, boolean activa) {
    }

    // ---------- Proveedores, compras y clientes ----------

    public record ProveedorRequest(
            @NotBlank @Size(max = 120) String nombreComercial,
            @Size(max = 120) String contacto,
            @Size(max = 30) String telefono,
            @Email(message = "Correo no válido") @Size(max = 120) String correo,
            @Size(max = 250) String direccion,
            @Size(max = 30) String nit,
            @Size(max = 30) String nrc,
            @Size(max = 500) String notas,
            boolean activo) {
    }

    public record CompraDetalleRequest(
            @NotNull Long itemId,
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "La cantidad debe ser mayor que cero") BigDecimal cantidad,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal costoUnitario,
            @Size(max = 40) String lote,
            LocalDate fechaVencimiento) {
    }

    public record CompraRequest(
            @NotNull(message = "Seleccione el proveedor") Long proveedorId,
            @NotNull LocalDate fecha,
            @Size(max = 40) String numeroFactura,
            LocalDate fechaVencimientoPago,
            @Size(max = 500) String notas,
            boolean recibirAhora,
            @NotEmpty(message = "Agregue al menos un artículo") @Valid List<CompraDetalleRequest> detalles) {
    }

    public record PagoCompraRequest(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal monto,
            @NotNull MetodoPago metodo,
            LocalDate fecha,
            @Size(max = 250) String nota,
            boolean desdeCaja) {
    }

    public record ClienteRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String nombre,
            @Size(max = 30) String telefono,
            @Size(max = 250) String direccion,
            @Email(message = "Correo no válido") @Size(max = 120) String correo,
            @Size(max = 30) String documento,
            @Size(max = 30) String nrc,
            @Size(max = 500) String notas,
            boolean activo) {
    }
}

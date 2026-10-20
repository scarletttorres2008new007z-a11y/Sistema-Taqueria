-- =====================================================================
-- Sistema de administracion para taqueria - esquema inicial
-- Base de datos: PostgreSQL 14 o superior
-- Montos: numeric(12,2). Cantidades: numeric(14,3). Costos unitarios: numeric(12,4)
-- =====================================================================

-- ---------- Seguridad ----------
CREATE TABLE roles (
    id           BIGSERIAL PRIMARY KEY,
    codigo       VARCHAR(30)  NOT NULL UNIQUE,
    nombre       VARCHAR(60)  NOT NULL,
    descripcion  VARCHAR(250),
    sistema      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE TABLE rol_permisos (
    rol_id   BIGINT      NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permiso  VARCHAR(50) NOT NULL,
    PRIMARY KEY (rol_id, permiso)
);

CREATE TABLE usuarios (
    id                     BIGSERIAL PRIMARY KEY,
    username               VARCHAR(50)  NOT NULL,
    nombre                 VARCHAR(120) NOT NULL,
    password_hash          VARCHAR(100) NOT NULL,
    rol_id                 BIGINT       NOT NULL REFERENCES roles(id),
    telefono               VARCHAR(30),
    activo                 BOOLEAN      NOT NULL DEFAULT TRUE,
    demo                   BOOLEAN      NOT NULL DEFAULT FALSE,
    ultimo_acceso          TIMESTAMPTZ,
    creado_en              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_usuarios_username ON usuarios (lower(username));

-- ---------- Configuracion del negocio (una sola fila) ----------
CREATE TABLE configuracion (
    id                   BIGINT PRIMARY KEY CHECK (id = 1),
    nombre_comercial     VARCHAR(120) NOT NULL,
    razon_social         VARCHAR(150),
    logo_url             VARCHAR(300),
    telefono             VARCHAR(30),
    correo               VARCHAR(120),
    direccion            VARCHAR(250),
    nit                  VARCHAR(30),
    nrc                  VARCHAR(30),
    giro                 VARCHAR(150),
    mensaje_comprobante  VARCHAR(250),
    iva_porcentaje       NUMERIC(5,2) NOT NULL DEFAULT 13.00 CHECK (iva_porcentaje >= 0 AND iva_porcentaje < 100),
    ancho_papel_mm       INTEGER      NOT NULL DEFAULT 80 CHECK (ancho_papel_mm IN (58, 80)),
    datos_demo           BOOLEAN      NOT NULL DEFAULT FALSE,
    actualizado_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
INSERT INTO configuracion (id, nombre_comercial, mensaje_comprobante)
VALUES (1, 'Mi Taquería (configure el nombre)', 'Gracias por su compra');

-- ---------- Catalogo ----------
CREATE TABLE categorias (
    id      BIGSERIAL PRIMARY KEY,
    nombre  VARCHAR(60) NOT NULL UNIQUE,
    orden   INTEGER     NOT NULL DEFAULT 0,
    activa  BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE unidades_medida (
    id      BIGSERIAL PRIMARY KEY,
    codigo  VARCHAR(10) NOT NULL UNIQUE,
    nombre  VARCHAR(40) NOT NULL,
    tipo    VARCHAR(10) NOT NULL CHECK (tipo IN ('MASA', 'VOLUMEN', 'UNIDAD')),
    -- cuantas unidades base contiene (base: gramo, mililitro o unidad)
    factor  NUMERIC(18,6) NOT NULL CHECK (factor > 0)
);

CREATE TABLE proveedores (
    id                BIGSERIAL PRIMARY KEY,
    nombre_comercial  VARCHAR(120) NOT NULL,
    contacto          VARCHAR(120),
    telefono          VARCHAR(30),
    correo            VARCHAR(120),
    direccion         VARCHAR(250),
    nit               VARCHAR(30),
    nrc               VARCHAR(30),
    notas             VARCHAR(500),
    activo            BOOLEAN     NOT NULL DEFAULT TRUE,
    demo              BOOLEAN     NOT NULL DEFAULT FALSE,
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventario_items (
    id                 BIGSERIAL PRIMARY KEY,
    codigo             VARCHAR(20)  NOT NULL UNIQUE,
    nombre             VARCHAR(100) NOT NULL,
    tipo               VARCHAR(20)  NOT NULL CHECK (tipo IN ('INGREDIENTE', 'PRODUCTO_TERMINADO', 'MATERIAL')),
    categoria          VARCHAR(40)  NOT NULL,
    unidad_id          BIGINT       NOT NULL REFERENCES unidades_medida(id),
    existencia         NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (existencia >= 0),
    existencia_minima  NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK (existencia_minima >= 0),
    costo_unitario     NUMERIC(12,4) NOT NULL DEFAULT 0 CHECK (costo_unitario >= 0),
    proveedor_id       BIGINT REFERENCES proveedores(id),
    fecha_vencimiento  DATE,
    lote               VARCHAR(40),
    activo             BOOLEAN     NOT NULL DEFAULT TRUE,
    demo               BOOLEAN     NOT NULL DEFAULT FALSE,
    version            BIGINT      NOT NULL DEFAULT 0,
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_items_nombre ON inventario_items (lower(nombre));

CREATE TABLE productos (
    id                  BIGSERIAL PRIMARY KEY,
    codigo              VARCHAR(20)  NOT NULL UNIQUE,
    nombre              VARCHAR(100) NOT NULL,
    descripcion         VARCHAR(500),
    categoria_id        BIGINT       NOT NULL REFERENCES categorias(id),
    tipo                VARCHAR(20)  NOT NULL CHECK (tipo IN ('PREPARADO', 'REVENTA', 'COMBO')),
    precio              NUMERIC(12,2) NOT NULL CHECK (precio >= 0),
    costo_estimado      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (costo_estimado >= 0),
    imagen_url          VARCHAR(300),
    -- Para productos de reventa (por ejemplo, agua embotellada): item de inventario que se descuenta
    item_inventario_id  BIGINT REFERENCES inventario_items(id),
    activo              BOOLEAN     NOT NULL DEFAULT TRUE,
    disponible          BOOLEAN     NOT NULL DEFAULT TRUE,
    demo                BOOLEAN     NOT NULL DEFAULT FALSE,
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_productos_categoria ON productos (categoria_id);

-- Opciones que se pueden agregar a un producto (queso extra, doble carne...)
CREATE TABLE complementos (
    id            BIGSERIAL PRIMARY KEY,
    nombre        VARCHAR(80)   NOT NULL,
    precio_extra  NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (precio_extra >= 0),
    categoria_id  BIGINT REFERENCES categorias(id),
    activo        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE recetas (
    id               BIGSERIAL PRIMARY KEY,
    producto_id      BIGINT NOT NULL UNIQUE REFERENCES productos(id),
    notas            VARCHAR(500),
    actualizado_por  VARCHAR(50),
    actualizado_en   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE receta_detalles (
    id         BIGSERIAL PRIMARY KEY,
    receta_id  BIGINT        NOT NULL REFERENCES recetas(id) ON DELETE CASCADE,
    item_id    BIGINT        NOT NULL REFERENCES inventario_items(id),
    cantidad   NUMERIC(14,4) NOT NULL CHECK (cantidad > 0),
    unidad_id  BIGINT        NOT NULL REFERENCES unidades_medida(id),
    UNIQUE (receta_id, item_id)
);

CREATE TABLE movimientos_inventario (
    id                   BIGSERIAL PRIMARY KEY,
    item_id              BIGINT        NOT NULL REFERENCES inventario_items(id),
    tipo                 VARCHAR(20)   NOT NULL CHECK (tipo IN ('ENTRADA', 'SALIDA', 'AJUSTE', 'VENTA', 'DEVOLUCION_VENTA', 'COMPRA')),
    -- positiva si aumenta la existencia, negativa si la reduce (en la unidad del item)
    cantidad             NUMERIC(14,3) NOT NULL,
    existencia_anterior  NUMERIC(14,3) NOT NULL,
    existencia_nueva     NUMERIC(14,3) NOT NULL,
    costo_unitario       NUMERIC(12,4),
    motivo               VARCHAR(250)  NOT NULL,
    referencia_tipo      VARCHAR(20),
    referencia_id        BIGINT,
    usuario_id           BIGINT REFERENCES usuarios(id),
    creado_en            TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_mov_inv_item ON movimientos_inventario (item_id, creado_en DESC);
CREATE INDEX ix_mov_inv_fecha ON movimientos_inventario (creado_en);
-- Una venta o compra no puede mover dos veces el mismo item con el mismo tipo
CREATE UNIQUE INDEX ux_mov_inv_referencia ON movimientos_inventario (referencia_tipo, referencia_id, item_id, tipo)
    WHERE referencia_id IS NOT NULL;

-- ---------- Clientes y mesas ----------
CREATE TABLE clientes (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(150) NOT NULL,
    telefono        VARCHAR(30),
    direccion       VARCHAR(250),
    correo          VARCHAR(120),
    documento       VARCHAR(30),
    nrc             VARCHAR(30),
    notas           VARCHAR(500),
    activo          BOOLEAN     NOT NULL DEFAULT TRUE,
    demo            BOOLEAN     NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_clientes_nombre ON clientes (lower(nombre));

CREATE TABLE mesas (
    id         BIGSERIAL PRIMARY KEY,
    nombre     VARCHAR(20) NOT NULL UNIQUE,
    capacidad  INTEGER     NOT NULL DEFAULT 4 CHECK (capacidad > 0),
    activa     BOOLEAN     NOT NULL DEFAULT TRUE
);

-- ---------- Pedidos ----------
CREATE TABLE pedidos (
    id                   BIGSERIAL PRIMARY KEY,
    tipo                 VARCHAR(12)  NOT NULL CHECK (tipo IN ('LOCAL', 'LLEVAR', 'DOMICILIO')),
    estado               VARCHAR(20)  NOT NULL CHECK (estado IN ('PENDIENTE', 'EN_PREPARACION', 'LISTO', 'ENTREGADO', 'CANCELADO')),
    mesa_id              BIGINT REFERENCES mesas(id),
    cliente_id           BIGINT REFERENCES clientes(id),
    cliente_nombre       VARCHAR(150),
    cliente_telefono     VARCHAR(30),
    direccion_entrega    VARCHAR(250),
    referencia_entrega   VARCHAR(250),
    notas                VARCHAR(300),
    subtotal             NUMERIC(12,2) NOT NULL DEFAULT 0,
    total                NUMERIC(12,2) NOT NULL DEFAULT 0,
    usuario_id           BIGINT NOT NULL REFERENCES usuarios(id),
    clave_idempotencia   VARCHAR(64) UNIQUE,
    version              BIGINT      NOT NULL DEFAULT 0,
    creado_en            TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_pedidos_estado ON pedidos (estado);
CREATE INDEX ix_pedidos_fecha ON pedidos (creado_en);

CREATE TABLE pedido_detalles (
    id               BIGSERIAL PRIMARY KEY,
    pedido_id        BIGINT        NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
    producto_id      BIGINT        NOT NULL REFERENCES productos(id),
    nombre_producto  VARCHAR(100)  NOT NULL,
    precio_base      NUMERIC(12,2) NOT NULL CHECK (precio_base >= 0),
    -- precio base + complementos (precio historico: no cambia si se modifica el menu)
    precio_unitario  NUMERIC(12,2) NOT NULL CHECK (precio_unitario >= 0),
    cantidad         INTEGER       NOT NULL CHECK (cantidad > 0),
    subtotal         NUMERIC(12,2) NOT NULL,
    observaciones    VARCHAR(200)
);
CREATE INDEX ix_pedido_detalles_pedido ON pedido_detalles (pedido_id);

CREATE TABLE pedido_detalle_complementos (
    id              BIGSERIAL PRIMARY KEY,
    detalle_id      BIGINT        NOT NULL REFERENCES pedido_detalles(id) ON DELETE CASCADE,
    complemento_id  BIGINT        NOT NULL REFERENCES complementos(id),
    nombre          VARCHAR(80)   NOT NULL,
    precio_extra    NUMERIC(12,2) NOT NULL
);

CREATE TABLE pedido_estados (
    id               BIGSERIAL PRIMARY KEY,
    pedido_id        BIGINT      NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
    estado_anterior  VARCHAR(20),
    estado_nuevo     VARCHAR(20) NOT NULL,
    motivo           VARCHAR(250),
    usuario_id       BIGINT REFERENCES usuarios(id),
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_pedido_estados_pedido ON pedido_estados (pedido_id);

-- ---------- Ventas, pagos y comprobantes ----------
CREATE TABLE ventas (
    id                    BIGSERIAL PRIMARY KEY,
    -- un pedido solo puede convertirse en una venta
    pedido_id             BIGINT UNIQUE REFERENCES pedidos(id),
    cliente_id            BIGINT REFERENCES clientes(id),
    cliente_nombre        VARCHAR(150),
    cliente_documento     VARCHAR(30),
    cliente_direccion     VARCHAR(250),
    estado                VARCHAR(20)   NOT NULL CHECK (estado IN ('PENDIENTE_PAGO', 'PAGADA', 'ANULADA')),
    subtotal              NUMERIC(12,2) NOT NULL,
    descuento             NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (descuento >= 0),
    -- total sin IVA y el IVA incluido en los precios
    base_imponible        NUMERIC(12,2) NOT NULL,
    iva_porcentaje        NUMERIC(5,2)  NOT NULL,
    impuesto              NUMERIC(12,2) NOT NULL,
    total                 NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    monto_pagado          NUMERIC(12,2) NOT NULL DEFAULT 0,
    costo_estimado        NUMERIC(12,2) NOT NULL DEFAULT 0,
    usuario_id            BIGINT NOT NULL REFERENCES usuarios(id),
    creado_en             TIMESTAMPTZ NOT NULL DEFAULT now(),
    anulada_en            TIMESTAMPTZ,
    anulada_por           BIGINT REFERENCES usuarios(id),
    motivo_anulacion      VARCHAR(250),
    inventario_devuelto   BOOLEAN NOT NULL DEFAULT FALSE,
    clave_idempotencia    VARCHAR(64) UNIQUE,
    version               BIGINT  NOT NULL DEFAULT 0
);
CREATE INDEX ix_ventas_fecha ON ventas (creado_en);
CREATE INDEX ix_ventas_estado ON ventas (estado);

CREATE TABLE venta_detalles (
    id               BIGSERIAL PRIMARY KEY,
    venta_id         BIGINT        NOT NULL REFERENCES ventas(id) ON DELETE CASCADE,
    producto_id      BIGINT        NOT NULL REFERENCES productos(id),
    descripcion      VARCHAR(250)  NOT NULL,
    cantidad         INTEGER       NOT NULL CHECK (cantidad > 0),
    precio_unitario  NUMERIC(12,2) NOT NULL CHECK (precio_unitario >= 0),
    costo_unitario   NUMERIC(12,2) NOT NULL DEFAULT 0,
    subtotal         NUMERIC(12,2) NOT NULL,
    observaciones    VARCHAR(200)
);
CREATE INDEX ix_venta_detalles_venta ON venta_detalles (venta_id);

CREATE TABLE cajas (
    id                   BIGSERIAL PRIMARY KEY,
    usuario_apertura_id  BIGINT        NOT NULL REFERENCES usuarios(id),
    monto_inicial        NUMERIC(12,2) NOT NULL CHECK (monto_inicial >= 0),
    abierta_en           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    usuario_cierre_id    BIGINT REFERENCES usuarios(id),
    cerrada_en           TIMESTAMPTZ,
    efectivo_esperado    NUMERIC(12,2),
    efectivo_contado     NUMERIC(12,2),
    diferencia           NUMERIC(12,2),
    estado               VARCHAR(10)   NOT NULL CHECK (estado IN ('ABIERTA', 'CERRADA')),
    notas                VARCHAR(500),
    version              BIGINT        NOT NULL DEFAULT 0
);
-- Solo puede haber una caja abierta a la vez
CREATE UNIQUE INDEX ux_cajas_una_abierta ON cajas (estado) WHERE estado = 'ABIERTA';

CREATE TABLE pagos (
    id                  BIGSERIAL PRIMARY KEY,
    venta_id            BIGINT        NOT NULL REFERENCES ventas(id),
    metodo              VARCHAR(15)   NOT NULL CHECK (metodo IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA')),
    monto               NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    monto_recibido      NUMERIC(12,2),
    cambio              NUMERIC(12,2),
    -- numero de autorizacion o de transferencia. Nunca se guardan datos de tarjetas.
    referencia          VARCHAR(60),
    caja_id             BIGINT REFERENCES cajas(id),
    estado              VARCHAR(12)   NOT NULL CHECK (estado IN ('APLICADO', 'REVERTIDO')),
    usuario_id          BIGINT        NOT NULL REFERENCES usuarios(id),
    clave_idempotencia  VARCHAR(64) UNIQUE,
    creado_en           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_pagos_venta ON pagos (venta_id);

CREATE TABLE comprobantes (
    id                 BIGSERIAL PRIMARY KEY,
    numero             VARCHAR(20)  NOT NULL UNIQUE,
    tipo               VARCHAR(20)  NOT NULL CHECK (tipo IN ('INTERNO')),
    -- una venta tiene un solo comprobante; reimprimir no crea otro
    venta_id           BIGINT       NOT NULL UNIQUE REFERENCES ventas(id),
    fecha_emision      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    negocio_nombre     VARCHAR(120) NOT NULL,
    negocio_direccion  VARCHAR(250),
    negocio_telefono   VARCHAR(30),
    negocio_nit        VARCHAR(30),
    negocio_nrc        VARCHAR(30),
    mensaje            VARCHAR(250),
    cliente_nombre     VARCHAR(150),
    cliente_documento  VARCHAR(30),
    cliente_direccion  VARCHAR(250),
    estado             VARCHAR(10)  NOT NULL CHECK (estado IN ('VIGENTE', 'ANULADO')),
    impresiones        INTEGER      NOT NULL DEFAULT 0,
    ultima_impresion   TIMESTAMPTZ,
    usuario_id         BIGINT       NOT NULL REFERENCES usuarios(id)
);
CREATE INDEX ix_comprobantes_fecha ON comprobantes (fecha_emision);

-- Preparada para la futura integracion de Documentos Tributarios Electronicos (DTE)
-- del Ministerio de Hacienda. No se usa hasta configurar y validar esa integracion.
CREATE TABLE documentos_tributarios (
    id                 BIGSERIAL PRIMARY KEY,
    venta_id           BIGINT      NOT NULL REFERENCES ventas(id),
    tipo_dte           VARCHAR(2)  NOT NULL,
    codigo_generacion  VARCHAR(36) UNIQUE,
    numero_control     VARCHAR(31) UNIQUE,
    estado             VARCHAR(20) NOT NULL CHECK (estado IN ('BORRADOR', 'FIRMADO', 'TRANSMITIDO', 'PROCESADO', 'RECHAZADO', 'INVALIDADO', 'CONTINGENCIA')),
    sello_recepcion    VARCHAR(100),
    json_documento     TEXT,
    respuesta_mh       TEXT,
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE movimientos_caja (
    id               BIGSERIAL PRIMARY KEY,
    caja_id          BIGINT        NOT NULL REFERENCES cajas(id),
    tipo             VARCHAR(20)   NOT NULL CHECK (tipo IN ('VENTA', 'DEVOLUCION', 'INGRESO', 'RETIRO', 'GASTO', 'PAGO_PROVEEDOR')),
    metodo           VARCHAR(15)   NOT NULL CHECK (metodo IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA')),
    -- positivo = entra dinero, negativo = sale dinero
    monto            NUMERIC(12,2) NOT NULL CHECK (monto <> 0),
    concepto         VARCHAR(250)  NOT NULL,
    referencia_tipo  VARCHAR(20),
    referencia_id    BIGINT,
    usuario_id       BIGINT        NOT NULL REFERENCES usuarios(id),
    creado_en        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_mov_caja_caja ON movimientos_caja (caja_id);
-- Un mismo pago, gasto o devolucion no puede registrarse dos veces en caja
CREATE UNIQUE INDEX ux_mov_caja_referencia ON movimientos_caja (referencia_tipo, referencia_id, tipo)
    WHERE referencia_id IS NOT NULL;

-- ---------- Gastos ----------
CREATE TABLE categorias_gasto (
    id      BIGSERIAL PRIMARY KEY,
    nombre  VARCHAR(60) NOT NULL UNIQUE,
    activa  BOOLEAN     NOT NULL DEFAULT TRUE
);
INSERT INTO categorias_gasto (nombre) VALUES
    ('Gas'), ('Agua'), ('Electricidad'), ('Alquiler'), ('Limpieza'), ('Transporte'),
    ('Mantenimiento'), ('Sueldos'), ('Otros');

CREATE TABLE gastos (
    id                  BIGSERIAL PRIMARY KEY,
    fecha               DATE          NOT NULL,
    categoria_id        BIGINT        NOT NULL REFERENCES categorias_gasto(id),
    concepto            VARCHAR(250)  NOT NULL,
    monto               NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    metodo_pago         VARCHAR(15)   NOT NULL CHECK (metodo_pago IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA')),
    pagado_desde_caja   BOOLEAN       NOT NULL DEFAULT FALSE,
    numero_comprobante  VARCHAR(60),
    comprobante_url     VARCHAR(300),
    anulado             BOOLEAN       NOT NULL DEFAULT FALSE,
    motivo_anulacion    VARCHAR(250),
    usuario_id          BIGINT        NOT NULL REFERENCES usuarios(id),
    demo                BOOLEAN       NOT NULL DEFAULT FALSE,
    creado_en           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_gastos_fecha ON gastos (fecha);

-- ---------- Compras ----------
CREATE TABLE compras (
    id                      BIGSERIAL PRIMARY KEY,
    proveedor_id            BIGINT        NOT NULL REFERENCES proveedores(id),
    fecha                   DATE          NOT NULL,
    numero_factura          VARCHAR(40),
    estado                  VARCHAR(20)   NOT NULL CHECK (estado IN ('PENDIENTE_RECEPCION', 'RECIBIDA', 'ANULADA')),
    fecha_recepcion         TIMESTAMPTZ,
    recibido_por            BIGINT REFERENCES usuarios(id),
    total                   NUMERIC(12,2) NOT NULL CHECK (total >= 0),
    monto_pagado            NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (monto_pagado >= 0),
    fecha_vencimiento_pago  DATE,
    notas                   VARCHAR(500),
    usuario_id              BIGINT        NOT NULL REFERENCES usuarios(id),
    demo                    BOOLEAN       NOT NULL DEFAULT FALSE,
    version                 BIGINT        NOT NULL DEFAULT 0,
    creado_en               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (monto_pagado <= total)
);
CREATE INDEX ix_compras_proveedor ON compras (proveedor_id);
CREATE INDEX ix_compras_fecha ON compras (fecha);

CREATE TABLE compra_detalles (
    id                 BIGSERIAL PRIMARY KEY,
    compra_id          BIGINT        NOT NULL REFERENCES compras(id) ON DELETE CASCADE,
    item_id            BIGINT        NOT NULL REFERENCES inventario_items(id),
    cantidad           NUMERIC(14,3) NOT NULL CHECK (cantidad > 0),
    costo_unitario     NUMERIC(12,4) NOT NULL CHECK (costo_unitario >= 0),
    subtotal           NUMERIC(12,2) NOT NULL,
    lote               VARCHAR(40),
    fecha_vencimiento  DATE,
    UNIQUE (compra_id, item_id)
);

CREATE TABLE pagos_compra (
    id                  BIGSERIAL PRIMARY KEY,
    compra_id           BIGINT        NOT NULL REFERENCES compras(id),
    fecha               DATE          NOT NULL,
    monto               NUMERIC(12,2) NOT NULL CHECK (monto > 0),
    metodo              VARCHAR(15)   NOT NULL CHECK (metodo IN ('EFECTIVO', 'TARJETA', 'TRANSFERENCIA')),
    desde_caja          BOOLEAN       NOT NULL DEFAULT FALSE,
    nota                VARCHAR(250),
    usuario_id          BIGINT        NOT NULL REFERENCES usuarios(id),
    creado_en           TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------- Auditoria e idempotencia ----------
CREATE TABLE auditoria (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT,
    usuario_nombre  VARCHAR(50),
    accion          VARCHAR(80)  NOT NULL,
    entidad         VARCHAR(40),
    entidad_id      BIGINT,
    detalle         VARCHAR(1000),
    ip              VARCHAR(60),
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_auditoria_fecha ON auditoria (creado_en DESC);

-- Respuestas guardadas para que un reintento (doble clic, red lenta) no repita la operacion
CREATE TABLE solicitudes_idempotentes (
    clave        VARCHAR(64)  PRIMARY KEY,
    usuario_id   BIGINT       NOT NULL,
    ruta         VARCHAR(200) NOT NULL,
    estado_http  INTEGER      NOT NULL,
    respuesta    TEXT,
    creado_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

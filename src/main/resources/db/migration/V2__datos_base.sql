-- Datos base necesarios para que el sistema funcione (no son datos de demostracion)

INSERT INTO roles (codigo, nombre, descripcion, sistema) VALUES
    ('ADMIN',      'Administrador / Dueño', 'Acceso completo, configuración y reportes', TRUE),
    ('CAJERO',     'Cajero',                'Toma pedidos, cobra, emite comprobantes y maneja la caja', TRUE),
    ('MESERO',     'Mesero',                'Toma pedidos y consulta su estado', TRUE),
    ('COCINA',     'Cocina',                'Consulta pedidos y actualiza su estado de preparación', TRUE),
    ('INVENTARIO', 'Encargado de inventario', 'Registra entradas, salidas, recepción de compras y consulta existencias', TRUE);

-- El administrador recibe todos los permisos desde el codigo; estos son los de los demas cargos.
INSERT INTO rol_permisos (rol_id, permiso)
SELECT r.id, p.permiso FROM roles r
JOIN (VALUES
    ('CAJERO', 'productos.ver'), ('CAJERO', 'inventario.ver'),
    ('CAJERO', 'pedidos.ver'), ('CAJERO', 'pedidos.crear'), ('CAJERO', 'pedidos.estado'), ('CAJERO', 'pedidos.cancelar'),
    ('CAJERO', 'ventas.ver'), ('CAJERO', 'ventas.cobrar'),
    ('CAJERO', 'comprobantes.ver'), ('CAJERO', 'comprobantes.imprimir'),
    ('CAJERO', 'caja.ver'), ('CAJERO', 'caja.operar'),
    ('CAJERO', 'gastos.ver'), ('CAJERO', 'gastos.gestionar'),
    ('CAJERO', 'clientes.ver'), ('CAJERO', 'clientes.gestionar'),
    ('MESERO', 'productos.ver'), ('MESERO', 'pedidos.ver'), ('MESERO', 'pedidos.crear'), ('MESERO', 'pedidos.estado'),
    ('MESERO', 'clientes.ver'), ('MESERO', 'clientes.gestionar'),
    ('COCINA', 'productos.ver'), ('COCINA', 'pedidos.ver'), ('COCINA', 'pedidos.estado'), ('COCINA', 'inventario.ver'),
    ('INVENTARIO', 'productos.ver'), ('INVENTARIO', 'inventario.ver'), ('INVENTARIO', 'inventario.entradas'),
    ('INVENTARIO', 'inventario.salidas'), ('INVENTARIO', 'inventario.ajustar'), ('INVENTARIO', 'inventario.gestionar'),
    ('INVENTARIO', 'compras.ver'), ('INVENTARIO', 'compras.recibir')
) AS p(rol, permiso) ON p.rol = r.codigo;

INSERT INTO unidades_medida (codigo, nombre, tipo, factor) VALUES
    ('g',   'Gramo',      'MASA',    1),
    ('kg',  'Kilogramo',  'MASA',    1000),
    ('lb',  'Libra',      'MASA',    453.592370),
    ('oz',  'Onza',       'MASA',    28.349523),
    ('ml',  'Mililitro',  'VOLUMEN', 1),
    ('l',   'Litro',      'VOLUMEN', 1000),
    ('gal', 'Galón',      'VOLUMEN', 3785.411784),
    ('u',   'Unidad',     'UNIDAD',  1),
    ('doc', 'Docena',     'UNIDAD',  12);

INSERT INTO categorias (nombre, orden) VALUES
    ('Tacos', 1), ('Tortas', 2), ('Quesadillas', 3), ('Burritos', 4), ('Combos', 5),
    ('Bebidas', 6), ('Acompañamientos', 7), ('Otros', 8);

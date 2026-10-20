# Sistema web de administración para taquería

Sistema para operar una taquería desde el navegador: toma de pedidos, panel de cocina en tiempo real,
ventas y cobros, comprobantes internos con impresión térmica (58/80 mm), inventario descontado por
recetas, caja, gastos, compras a proveedores, clientes, empleados con permisos, reportes y panel principal.

Configurado para El Salvador: dólares (USD), zona horaria America/El_Salvador, fechas dd/mm/aaaa, IVA 13 %
incluido en los precios (configurable). Funciona en Windows, Linux y macOS.

> **Importante**
> - Los comprobantes son **internos**: no son facturas ni documentos tributarios electrónicos (DTE).
>   La facturación electrónica de Hacienda **no está integrada**. Ver [docs/FACTURACION_ELECTRONICA.md](docs/FACTURACION_ELECTRONICA.md).
> - La impresión se probó generando el PDF de 58 y 80 mm, **no en una impresora SAT física** (pendiente de
>   validación real). Ver [docs/IMPRESORA_TERMICA.md](docs/IMPRESORA_TERMICA.md).
> - El sistema no trae nombre, NIT ni NRC de ningún negocio real. Los datos de demostración son opcionales
>   y dicen "(demo)".

## Contenido

1. [Arquitectura](#1-arquitectura)
2. [Estructura de carpetas](#2-estructura-de-carpetas)
3. [Tecnologías](#3-tecnologías)
4. [Modelo de base de datos](#4-modelo-de-base-de-datos)
5. [Instalación en Windows](#5-instalación-en-windows)
6. [Configurar la base de datos](#6-configurar-la-base-de-datos)
7. [Iniciar el sistema](#7-iniciar-el-sistema)
8. [Acceso y creación del administrador](#8-acceso-y-creación-del-administrador)
9. [Datos de demostración (opcional)](#9-datos-de-demostración-opcional)
10. [Funciones](#10-funciones)
11. [Pruebas y resultados](#11-pruebas-y-resultados)
12. [Impresora térmica SAT](#12-impresora-térmica-sat)
13. [Seguridad](#13-seguridad)
14. [Respaldo y restauración](#14-respaldo-y-restauración)
15. [Detener y reiniciar](#15-detener-y-reiniciar)
16. [Limitaciones conocidas](#16-limitaciones-conocidas)
17. [API](#17-api)

---

## 1. Arquitectura

```
 Navegador (PC de caja, tablet de cocina, celular del mesero)
   │  Páginas HTML + CSS + JavaScript (Bootstrap)  ── fetch JSON ──┐
   │  Panel de cocina en tiempo real  ◄── eventos del servidor (SSE)│
   ▼                                                                ▼
 Servidor Java (Spring Boot, un solo archivo taqueria.jar, puerto 8080)
   ├─ Seguridad: sesión, contraseñas BCrypt, permisos por cargo, CSRF, bloqueo por intentos
   ├─ API REST /api/...  (controladores → servicios con las reglas del negocio)
   ├─ Archivos subidos (logo, fotos, comprobantes de gastos) en la carpeta uploads/
   ▼
 PostgreSQL (tablas creadas y actualizadas automáticamente con Flyway)
```

- **Un solo programa**: el servidor Java entrega las páginas y la API. No hay que instalar un servidor web aparte.
- **Reglas en el servidor**: precios, totales, IVA, cambio, existencias y permisos se calculan y validan en
  Java, no en el navegador. Aunque alguien manipule la página, el servidor rechaza lo indebido.
- **Operaciones críticas en transacciones**: una venta registra pago, movimiento de caja, descuento de
  inventario y comprobante juntos; si algo falla no queda nada a medias.

### Por qué este frontend (HTML, CSS y JavaScript con Bootstrap)

Se eligió HTML/CSS/JavaScript sin compilación, con **Bootstrap 5** (diseño adaptable y componentes),
**Bootstrap Icons**, **Chart.js** (gráficos), **jsPDF** (PDF) y **SheetJS** (Excel), en lugar de React o Angular:

- **No requiere Node.js ni un paso de compilación**: Spring Boot sirve los archivos tal cual, y la
  instalación en Windows se reduce a Java y PostgreSQL.
- **Funciona sin internet**: todas las librerías están incluidas en `src/main/resources/static/vendor`, sin CDN.
  La taquería puede operar en su red local aunque se caiga el internet.
- **Liviano** para computadoras de caja modestas y tablets.
- **Fácil de mantener**: cada pantalla es un HTML y un archivo JS; las piezas comunes (menú, sesión,
  cobro, ticket) están en `js/comun/`.
- **Adaptable** a computadora, tablet y celular con las clases de Bootstrap.

## 2. Estructura de carpetas

```
Sistema-Taqueria/
├─ iniciar.bat / iniciar.sh            Inicia el sistema
├─ respaldo.bat / respaldo.sh          Respaldo de base de datos y archivos
├─ ejecutar-pruebas.bat / .sh          Pruebas automáticas
├─ .env.example                        Plantilla de configuración (copiar como .env)
├─ mvnw / mvnw.cmd / .mvn/             Maven incluido (no hay que instalarlo)
├─ pom.xml                             Dependencias Java
├─ scripts/crear-base-de-datos.sql     Crea usuario y bases en PostgreSQL
├─ docs/
│  ├─ IMPRESORA_TERMICA.md
│  └─ FACTURACION_ELECTRONICA.md
├─ pruebas-navegador/impresion.js      Prueba del formato de impresión con navegador real
└─ src/
   ├─ main/java/com/taqueria/
   │  ├─ TaqueriaApplication.java      Punto de entrada
   │  ├─ config/                       Seguridad, archivos estáticos, datos de demostración
   │  ├─ security/                     Sesión, permisos, idempotencia, bloqueo de intentos
   │  ├─ model/                        Entidades (tablas) y estados
   │  ├─ repository/                   Acceso a datos
   │  ├─ service/                      Reglas del negocio (pedidos, ventas, inventario, caja...)
   │  ├─ dto/                          Datos que recibe y devuelve la API (con validaciones)
   │  └─ web/                          Controladores de la API REST
   ├─ main/resources/
   │  ├─ application.properties        Configuración (lee variables de entorno / .env)
   │  ├─ db/migration/                 V1 esquema, V2 datos base (cargos, unidades, categorías)
   │  └─ static/                       Páginas HTML, css/, js/, js/comun/, img/, vendor/
   └─ test/java/com/taqueria/          Pruebas automáticas (P01 a P06)
```

## 3. Tecnologías

| Parte | Tecnología |
|-------|-----------|
| Lenguaje servidor | Java 17 o superior |
| Framework | Spring Boot 3.3 (Web, Data JPA, Security, Validation) |
| Base de datos | PostgreSQL 14 o superior (probado con 16) |
| Migraciones | Flyway |
| Construcción | Maven 3.9 (incluido con `mvnw`) |
| Frontend | HTML5, CSS3, JavaScript (módulos ES), Bootstrap 5.3, Bootstrap Icons |
| Gráficos y exportación | Chart.js 4, jsPDF + AutoTable, SheetJS (Excel) |
| Tiempo real | Server-Sent Events (SSE) con respaldo por consulta periódica |
| Pruebas | JUnit 5, Spring Boot Test, MockMvc; Playwright para la prueba de impresión |

## 4. Modelo de base de datos

33 tablas, creadas por `src/main/resources/db/migration/V1__esquema_inicial.sql`. Importes en `NUMERIC(12,2)`,
cantidades de inventario en `NUMERIC(14,3)`, fechas en UTC (se muestran en hora de El Salvador).

| Área | Tablas | Notas |
|------|--------|-------|
| Usuarios | `roles`, `rol_permisos`, `usuarios` | 5 cargos: Administrador, Cajero, Mesero, Cocina, Encargado de inventario |
| Negocio | `configuracion` | Nombre, NIT/NRC (opcionales), IVA, ancho de papel, logo |
| Menú | `categorias`, `productos`, `complementos`, `mesas` | Productos PREPARADO (con receta) o REVENTA (descuenta un artículo) |
| Inventario | `unidades_medida`, `inventario_items`, `recetas`, `receta_detalles`, `movimientos_inventario` | Toda variación de existencia tiene su movimiento con usuario, existencia anterior y nueva |
| Pedidos | `pedidos`, `pedido_detalles`, `pedido_detalle_complementos`, `pedido_estados` | Precio histórico por línea; historial de estados con usuario y motivo |
| Ventas | `ventas`, `venta_detalles`, `pagos`, `comprobantes`, `documentos_tributarios` | `ventas.pedido_id` único (un pedido = una venta); `comprobantes.venta_id` único; `documentos_tributarios` reservada para DTE (vacía) |
| Caja | `cajas`, `movimientos_caja` | Solo una caja abierta a la vez (índice único parcial) |
| Gastos | `categorias_gasto`, `gastos` | |
| Compras | `proveedores`, `compras`, `compra_detalles`, `pagos_compra` | |
| Clientes | `clientes` | |
| Control | `auditoria`, `solicitudes_idempotentes` | Bitácora de acciones; claves para evitar operaciones repetidas |

Relaciones principales: `pedido 1─N pedido_detalles 1─N complementos`; `pedido 1─0..1 venta 1─N pagos`;
`venta 1─1 comprobante`; `venta 1─N movimientos_inventario (VENTA)`; `pago 1─1 movimiento_caja`;
`compra 1─N compra_detalles → movimientos_inventario (COMPRA)`; `producto 1─0..1 receta 1─N receta_detalles → inventario_items`.

## 5. Instalación en Windows

1. **Java 17 o superior** (JDK). Por ejemplo *Eclipse Temurin 17/21* desde adoptium.net. Marque
   "Set JAVA_HOME" y "Add to PATH" durante la instalación. Compruebe en una ventana de comandos (cmd):
   ```
   java -version
   ```
2. **PostgreSQL 14 o superior** desde postgresql.org (instalador para Windows). Anote la contraseña del
   usuario `postgres` que le pide el instalador. Deje el puerto 5432.
3. Descomprima el ZIP del sistema en una carpeta, por ejemplo `C:\Taqueria`.

No necesita instalar Maven ni Node.js: `mvnw.cmd` descarga Maven la primera vez (requiere internet solo esa vez).

## 6. Configurar la base de datos

1. Abra **cmd** en la carpeta del sistema y ejecute (cambie la contraseña por una suya, segura):
   ```
   "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -v clave="ElijaUnaClaveSegura" -f scripts\crear-base-de-datos.sql
   ```
   Le pedirá la contraseña del usuario `postgres`. Esto crea el usuario `taqueria` y las bases `taqueria`
   (la real) y `taqueria_test` (solo para pruebas).
2. Copie `.env.example` como `.env` y en `DB_PASSWORD=` escriba la contraseña elegida en el paso 1.
   El archivo `.env` contiene su contraseña: no lo comparta.

Las tablas se crean solas al iniciar el sistema por primera vez.

## 7. Iniciar el sistema

**Windows**: doble clic en `iniciar.bat`. La primera vez compila el sistema (unos minutos).

**Manual (cualquier sistema operativo)**, desde la carpeta del sistema:
```
mvnw.cmd -DskipTests package          (Windows)   |   ./mvnw -DskipTests package   (Linux/macOS)
java -jar target/taqueria.jar
```
El sistema lee el archivo `.env` de la carpeta desde la que se inicia. También acepta las mismas
variables como variables de entorno.

Cuando la ventana muestre `Started TaqueriaApplication`, abra en el navegador:

- En la misma computadora: **http://localhost:8080**
- Desde una tablet o celular en la misma red: `http://IP-DE-LA-COMPUTADORA:8080` (vea la IP con `ipconfig`).
  Windows puede pedir permiso en el Firewall para Java la primera vez: permítalo solo en **redes privadas**.

## 8. Acceso y creación del administrador

El sistema **no trae ningún usuario ni contraseña fija**. La primera vez:

1. Al iniciar, la ventana del servidor muestra un recuadro:
   ```
   ==============================================================
    CONFIGURACION INICIAL PENDIENTE
    Abra el sistema en el navegador y cree el administrador.
    Codigo de configuracion (un solo uso): ABCD-EFGH-JKLM
   ==============================================================
   ```
2. Abra http://localhost:8080. Aparece **Configuración inicial**: escriba ese código, su usuario, nombre y
   una contraseña de al menos 8 caracteres.
3. El código solo sirve mientras no exista ningún usuario y cambia en cada inicio y tras 10 intentos fallidos.
   Así, alguien que llegue primero a la página no puede adueñarse del sistema sin ver la consola del servidor.
4. Entre con el administrador y vaya a **Configuración** para registrar los datos de su negocio (nombre,
   dirección, teléfono; NIT y NRC solo si aplica), el ancho del papel y el logo.
5. En **Empleados y permisos** cree un usuario para cada empleado con su cargo. Puede ajustar qué puede
   hacer cada cargo; los cambios obligan a esos empleados a volver a iniciar sesión.

Si olvida la contraseña del único administrador, otro administrador puede restablecerla desde
*Empleados y permisos*. Si no hay otro, consulte con quien administre la base de datos.

## 9. Datos de demostración (opcional)

Para probar el sistema con datos de ejemplo, **en una base vacía**:

1. En `.env` ponga `CARGAR_DEMO=true` y `DEMO_PASSWORD=` con una contraseña de al menos 8 caracteres.
2. Inicie el sistema. Se crean productos, recetas, inventario, proveedores y un cliente, todos marcados
   "(demo)", y los usuarios `admin.demo`, `cajero.demo`, `mesero.demo`, `cocina.demo`, `inventario.demo`
   con la contraseña que usted definió. Todas las pantallas muestran un aviso de datos de demostración.
3. Para operar el negocio real, use una base nueva sin `CARGAR_DEMO` (o borre y vuelva a crear la base `taqueria`).

Los precios y nombres de demostración son ejemplos; no representan a ningún negocio real.

## 10. Funciones

| Módulo | Qué hace |
|--------|----------|
| **Panel principal** | Ventas de hoy y del período, pedidos pendientes, en preparación y listos, ventas brutas y netas, descuentos, IVA, costo estimado, gastos, utilidad estimada, gráficos de ventas por día y productos más vendidos, alertas de caja, existencias bajas, vencimientos y deudas |
| **Tomar pedido** | Menú por categorías con búsqueda, carrito, cantidades, observaciones por producto ("sin cebolla"), complementos con precio extra, tipo local (mesa), para llevar o domicilio (datos del cliente), envío a cocina o cobro directo |
| **Pedidos** | Búsqueda por número, fecha, estado y tipo; detalle con historial; cambio de estado; cancelación con motivo |
| **Panel de cocina** | Columnas Pendiente / En preparación / Listo, tiempo transcurrido, observaciones resaltadas, sonido y resaltado de pedidos nuevos, actualización en tiempo real sin recargar |
| **Ventas** | Cobro con efectivo (cambio calculado), tarjeta (solo autorización o últimos 4 dígitos), transferencia o pago mixto; descuento con motivo; ventas pendientes de pago y abonos; anulación con motivo y opción de devolver inventario |
| **Comprobantes** | Comprobante interno numerado (CI-00000001), búsqueda por número de comprobante o de venta, fecha, cliente y estado; impresión térmica 58/80 mm, PDF, reimpresión marcada sin duplicar |
| **Caja** | Apertura con fondo, entradas y retiros con motivo, efectivo esperado en vivo, cierre con conteo y diferencia, historial de cierres |
| **Gastos** | Por categoría, método de pago, salida de caja opcional, adjunto (foto/PDF), anulación |
| **Productos y menú** | Productos con foto, código, precio, costo, disponibilidad; categorías, complementos y mesas |
| **Recetas** | Ingredientes por producto con conversión de unidades (g/kg, ml/l, unidad/docena), costo y margen estimados, estado de configuración |
| **Inventario** | Artículos con existencia mínima, lote y vencimiento; entradas, salidas y ajustes por conteo físico con motivo; movimientos con usuario; alertas |
| **Compras y proveedores** | Compras con detalle, recepción que suma al inventario (una sola vez), pagos parciales, deudas por proveedor |
| **Clientes** | Datos de contacto e historial de compras |
| **Reportes** | 11 reportes: ventas, utilidad, productos más vendidos, métodos de pago, gastos, compras por proveedor, movimientos de inventario, existencias bajas, pedidos por estado, anulaciones, cierres de caja. Agrupación por día/semana/mes. Exportación CSV, Excel y PDF |
| **Empleados y permisos** | Usuarios con cargo, activar/desactivar, restablecer contraseña, matriz de permisos por cargo |
| **Auditoría** | Bitácora de inicios de sesión, ventas, anulaciones, cambios de precios, ajustes, cierres, etc. |
| **Configuración** | Datos del negocio, IVA, papel, logo, página de prueba de impresión, estado de facturación electrónica |

### Flujo completo (verificado de punta a punta en el navegador)

1. El mesero toma el pedido (2 tacos de asada con queso extra "sin cebolla" y 1 horchata, mesa 1) y lo envía a cocina.
2. El pedido aparece al instante en el panel de cocina con su observación.
3. Cocina lo pasa a *En preparación* y luego a *Listo*.
4. El cajero cobra: total $5.50, recibe $20.00, el sistema muestra cambio $14.50.
5. Se registra la venta, el pago, el movimiento de caja, se descuentan los ingredientes según las recetas
   y se emite el comprobante interno.
6. Se imprime el comprobante (58 u 80 mm); la reimpresión dice REIMPRESIÓN y no crea otro comprobante.
7. El panel principal y los reportes reflejan la venta.

## 11. Pruebas y resultados

### Pruebas automáticas (JUnit)

Se ejecutan contra una base aparte (`taqueria_test`) que **se borra y se vuelve a crear** en cada
ejecución. Por seguridad, se niegan a correr si la dirección de la base no contiene "test".

```
ejecutar-pruebas.bat          (Windows)
./ejecutar-pruebas.sh         (Linux/macOS)
```

**Resultado real de la última ejecución: 29 casos, 0 fallos, 0 errores** (PostgreSQL 16, Java 21).

| # | Prueba exigida | Clase / método | Resultado |
|---|----------------|----------------|-----------|
| 1 | Inicio de sesión | P01 `inicioYCierreDeSesion`, `administradorInicialSoloConCodigoDeConsola`, `sinSesionNoHayAcceso` | Aprobada |
| 2 | Permisos | P01 `permisosSegunElCargo`, `empleadoDesactivadoPierdeLaSesionAbierta`, `cambioDePermisosDelCargoExigeVolverAEntrar` | Aprobada |
| 3 | Productos | P02 `prueba03_productos` | Aprobada |
| 4 | Ingredientes | P02 `prueba04_ingredientes` | Aprobada |
| 5 | Recetas | P02 `prueba05_recetas` | Aprobada |
| 6 | Entradas y salidas de inventario | P02 `prueba06_entradasSalidasYAjustes` | Aprobada |
| 7 | Pedido con varios productos | P03 `prueba07_pedidoConVariosProductos` | Aprobada |
| 8 | Persistencia | P03 `prueba08_persistenciaDelPedido` | Aprobada |
| 9 | Cocina | P03 `prueba09_panelDeCocina` | Aprobada |
| 10 | Estados | P03 `prueba10_estadosDelPedido` | Aprobada |
| 11 | Venta | P04 `prueba11_ventaDesdeUnPedido` | Aprobada |
| 12 | Pago | P04 `prueba12_pagos` | Aprobada |
| 13 | Subtotales y totales | P04 `prueba13_subtotalesDescuentosEIva` | Aprobada |
| 14 | Comprobante | P04 `prueba14_comprobanteInterno` | Aprobada |
| 15 | Consulta de comprobantes | P04 `prueba15_consultaDeComprobantes` | Aprobada |
| 16 | Reimpresión sin duplicar | P04 `prueba16_reimpresionSinDuplicar` | Aprobada |
| 17 | Descuento de inventario | P04 `prueba17_descuentoDeInventarioPorReceta` | Aprobada |
| 18 | Cancelación y devolución | P04 `prueba18_anulacionYDevolucion` | Aprobada |
| 19 | Apertura y cierre de caja | P05 `prueba19_aperturaYCierreDeCaja` | Aprobada |
| 20 | Gastos | P05 `prueba20_gastos` | Aprobada |
| 21 | Compras e inventario | P05 `prueba21_comprasEInventario` | Aprobada |
| 22 | Reportes | P06 `prueba22_reportes` | Aprobada |
| 23 | Validación | P06 `prueba23_validacionDeDatos` | Aprobada |
| 24 | Duplicados (doble clic, cobros simultáneos desde 6 hilos) | P06 `prueba24_prevencionDeDuplicados` | Aprobada |
| 25 | Diseño de impresión térmica | P06 `prueba25_disenoDeImpresionTermica` + `pruebas-navegador/impresion.js` | Aprobada en PDF; **impresora física: pendiente de validación real** |

### Pruebas en navegador (Chromium)

- Recorrido de todas las pantallas con los 5 cargos: sin errores de JavaScript (salvo el aviso esperado "Sin
  permiso" cuando un cargo abre una pantalla que no le corresponde).
- Flujo completo de la sección 10, incluido doble clic en *Confirmar cobro*: una sola venta, un pago y un
  movimiento de caja.
- Impresión (`pruebas-navegador/impresion.js`): PDF de **una página de 57.8 mm** (58 mm) y **80.1 mm** (80 mm),
  solo el ticket visible al imprimir, con la leyenda de comprobante interno.
- Exportación de reportes a CSV, Excel y PDF; vista de celular (390 px) sin desplazamiento horizontal.

Para repetir la prueba de impresión (requiere Node.js y el sistema en marcha con al menos una venta):
```
cd pruebas-navegador
npm install
npx playwright install chromium
set USUARIO=su_usuario
set CLAVE=su_contraseña
node impresion.js
```

### Pendiente de validación real

- Impresión en una impresora térmica SAT física (modelo no conocido).
- Facturación electrónica con Hacienda (no integrada).
- Los scripts `.bat` no se pudieron ejecutar en un Windows real. Sus equivalentes `.sh` sí se probaron en Linux
  (inicio leyendo `.env`, respaldo con restauración verificada en otra base, y pruebas). Confirme `iniciar.bat`,
  `respaldo.bat` y `ejecutar-pruebas.bat` en la computadora de la taquería.

## 12. Impresora térmica SAT

Resumen (detalle en [docs/IMPRESORA_TERMICA.md](docs/IMPRESORA_TERMICA.md)):

1. Instale el controlador del fabricante en Windows y configure el papel (58 u 80 mm) y márgenes mínimos.
2. En **Configuración** elija el ancho del papel.
3. Abra **Configuración → Abrir página de prueba** e imprímala con márgenes *Ninguno*, escala 100 % y sin
   encabezados ni pies de página.
4. Imprima un comprobante desde *Ventas* o *Comprobantes*.
5. Opcional: acceso directo de Chrome con `--kiosk-printing` para imprimir sin el cuadro de diálogo.

El sistema imprime a través del navegador. No se conecta a la impresora por USB o Bluetooth ni expone un
servicio de impresión.

## 13. Seguridad

- Contraseñas guardadas con **BCrypt**; nunca en texto plano. Sin usuarios ni contraseñas fijas en el código.
- Credenciales de la base de datos por variables de entorno o `.env` (excluido de Git).
- Permisos verificados en el **servidor** en cada operación, no solo ocultando botones.
- Bloqueo de 5 minutos tras 5 intentos fallidos de inicio de sesión por usuario.
- Si se desactiva un empleado o se cambian los permisos de su cargo, su sesión abierta se cierra.
- Protección CSRF, cookies de sesión HttpOnly y SameSite, política de seguridad de contenido (CSP) sin scripts en línea.
- **Idempotencia**: cada envío lleva una clave de operación; un doble clic o un reintento de red no crea
  pedidos, ventas ni pagos duplicados. Además, la base impide dos ventas por pedido y dos comprobantes por venta.
- No se guardan datos de tarjetas: se rechaza una referencia que parezca un número de tarjeta completo.
- Ninguna existencia cambia sin su movimiento de inventario con usuario y motivo.
- Auditoría de las acciones importantes.
- Si publica el sistema fuera de la red local, póngalo detrás de HTTPS y use `COOKIE_SECURE=true`.

## 14. Respaldo y restauración

**Respaldo**: doble clic en `respaldo.bat` (Windows) o `./respaldo.sh`. Crea en `respaldos/`:
- `taqueria-AAAAMMDD-HHMM.dump`: la base de datos completa (formato de `pg_dump`).
- `archivos-AAAAMMDD-HHMM.zip`: logo, fotos y comprobantes de gastos.

Si `pg_dump` no se encuentra, agregue `C:\Program Files\PostgreSQL\16\bin` al PATH de Windows.
Haga respaldos diarios y copie la carpeta `respaldos` a otro lugar (USB o nube).
Para programarlo: *Programador de tareas de Windows → Crear tarea básica → Diariamente → Iniciar un programa → respaldo.bat*.

**Restaurar** (detenga el sistema primero; esto reemplaza los datos actuales):
```
"C:\Program Files\PostgreSQL\16\bin\pg_restore.exe" -U taqueria -h localhost -d taqueria --clean --if-exists respaldos\taqueria-AAAAMMDD-HHMM.dump
```
Luego descomprima el ZIP de archivos dentro de la carpeta `uploads`.

## 15. Detener y reiniciar

- **Detener**: en la ventana donde corre el sistema presione `Ctrl+C` (confirme con `S`) o cierre la ventana.
- **Reiniciar**: vuelva a ejecutar `iniciar.bat`.
- **Después de actualizar el código**: borre la carpeta `target` (o ejecute `mvnw.cmd -DskipTests package`) e inicie de nuevo.
  Las migraciones nuevas de la base se aplican solas.
- **Iniciar con Windows** (opcional): ponga un acceso directo a `iniciar.bat` en la carpeta que abre
  `Win+R → shell:startup`.
- PostgreSQL se instala como servicio de Windows y arranca solo; si no, inícielo en *Servicios → postgresql-x64-16*.

## 16. Limitaciones conocidas

- **Facturación electrónica (DTE) no integrada**; solo comprobantes internos.
- **Impresora térmica física no probada**; sin impresión directa ESC/POS ni apertura de cajón de dinero.
- **Una sola sucursal y una sola caja abierta a la vez.**
- Los números de pedido, venta y comprobante son consecutivos, pero si una operación falla (por ejemplo,
  falta de existencias al cobrar) puede quedar un número sin usar. No se reutilizan números.
- Las fechas en los filtros usan el selector de fecha del navegador; su formato visual depende del idioma
  de Windows/navegador (en español de El Salvador se ve dd/mm/aaaa). En pantallas, tickets y reportes las
  fechas se muestran siempre como dd/mm/aaaa.
- El panel de cocina necesita mantener la página abierta; si se pierde la conexión, reintenta solo y
  mientras tanto consulta cada 20 segundos.
- Los costos y la utilidad son **estimados** según recetas y costos registrados.
- El sistema no envía correos ni mensajes a clientes.

## 17. API

Todas las rutas están bajo `/api`, requieren sesión (salvo `/api/sesion/estado` y la configuración inicial)
y devuelven JSON. Errores: `{"mensaje": "...", "campos": {"campo": "motivo"}}` con códigos 400 (datos no
válidos), 401 (sin sesión), 403 (sin permiso), 404 (no existe) y 409 (conflicto: duplicado, estado no permitido,
existencias insuficientes, caja cerrada).

| Grupo | Rutas principales |
|-------|-------------------|
| Sesión | `POST /api/sesion/entrar`, `POST /api/sesion/salir`, `GET /api/sesion/estado`, `POST /api/sesion/password`, `POST /api/configuracion-inicial/administrador` |
| Catálogo | `/api/categorias`, `/api/productos`, `/api/productos/{id}/desactivar`, `/api/productos/{id}/imagen`, `/api/complementos`, `/api/mesas`, `/api/recetas/{productoId}` |
| Inventario | `/api/unidades`, `/api/inventario`, `/api/inventario/movimientos`, `/api/inventario/alertas` |
| Pedidos | `POST /api/pedidos`, `GET /api/pedidos`, `/api/pedidos/{id}`, `/api/pedidos/cocina`, `/api/pedidos/eventos` (SSE), `PUT /api/pedidos/{id}/estado`, `POST /api/pedidos/{id}/cancelar`, `GET /api/pedidos/{id}/historial` |
| Ventas | `POST /api/pedidos/{id}/venta`, `POST /api/ventas/directa`, `/api/ventas`, `/api/ventas/pendientes`, `POST /api/ventas/{id}/pagos`, `POST /api/ventas/{id}/anular` |
| Comprobantes | `/api/comprobantes`, `/api/comprobantes/{id}`, `/api/comprobantes/venta/{ventaId}`, `POST /api/comprobantes/{id}/impresiones` |
| Caja y gastos | `/api/caja/actual`, `/api/caja/abrir`, `/api/caja/cerrar`, `/api/caja/movimientos`, `/api/caja/cierres`, `/api/gastos`, `/api/categorias-gasto` |
| Compras y clientes | `/api/proveedores`, `/api/compras`, `/api/compras/{id}/recibir`, `/api/compras/{id}/pagos`, `/api/compras/deudas`, `/api/clientes`, `/api/clientes/{id}/historial` |
| Reportes | `/api/dashboard`, `/api/reportes`, `/api/reportes/{tipo}`, `/api/reportes/{tipo}/csv` |
| Administración | `/api/configuracion`, `/api/usuarios`, `/api/roles/{id}/permisos`, `/api/permisos`, `/api/auditoria` |

Las operaciones que crean o modifican datos aceptan la cabecera `Idempotency-Key` (8 a 64 caracteres).

# Impresora térmica SAT: instalación y prueba

> **Estado: pendiente de validación real.** El formato del ticket se probó generando el PDF de impresión
> en Chromium (58 mm y 80 mm, una sola página, solo el ticket visible). **No se probó con una impresora
> SAT física**, porque no se tuvo acceso a una ni se conoce el modelo exacto. Los pasos de abajo son el
> procedimiento recomendado; anote el resultado real cuando lo haga.

## Cómo imprime el sistema

1. En **Ventas** o **Comprobantes** se abre el comprobante interno de la venta.
2. El botón **Imprimir** registra la impresión en el servidor (contador de impresiones; la segunda vez y
   siguientes el ticket dice **REIMPRESIÓN**) y abre el cuadro de impresión del navegador.
3. Usted elige la impresora térmica en ese cuadro. El navegador envía el trabajo al controlador de Windows.

El sistema **no** se conecta directamente a la impresora por USB, Bluetooth ni red: una página web no
puede hacerlo por seguridad, y no se instaló ningún servicio local de impresión. Así cualquier impresora
instalada en Windows funciona, y ninguna página externa puede enviarle órdenes.

El ticket se dibuja con medidas en milímetros:

| Papel | Ancho del ticket | Caracteres por línea (PDF) |
|-------|------------------|----------------------------|
| 58 mm | 58 mm            | 32                         |
| 80 mm | 80 mm            | 42                         |

La altura de la página se calcula según el contenido, para que la impresora corte al final del ticket y no
avance papel en blanco.

## Paso a paso (Windows)

1. **Identifique el modelo** de la impresora (etiqueta debajo del equipo) y el ancho del rollo (58 u 80 mm).
2. **Instale el controlador** del fabricante para ese modelo. Conecte la impresora por USB y verifique en
   *Configuración → Bluetooth y dispositivos → Impresoras y escáneres* que aparezca.
3. En las **preferencias de impresión** de esa impresora:
   - Tamaño de papel: el del rollo (por ejemplo *58 x 210 mm*, *80 x 297 mm* o "Roll paper").
   - Márgenes: los mínimos.
   - Corte automático al final del documento, si el modelo lo tiene.
4. Imprima la **página de prueba de Windows** para confirmar que el controlador funciona.
5. En el sistema, vaya a **Configuración** y elija el ancho de papel (58 u 80 mm). Guarde.
6. Abra **Configuración → Abrir página de prueba**. Esta página no crea ventas ni comprobantes.
   En el cuadro de impresión:
   - Destino: la impresora térmica.
   - Márgenes: *Ninguno*.
   - Escala: *Predeterminada* (100 %).
   - Desmarque *Encabezados y pies de página*.
7. Revise el papel: la regla de prueba debe verse completa de lado a lado, sin cortes, y el texto legible.
8. Haga una venta de prueba, imprima el comprobante y una reimpresión. Verifique que la segunda diga
   REIMPRESIÓN y que el contador en *Comprobantes* aumente.

Chrome y Edge recuerdan la impresora y las opciones del último uso, así que los pasos 6 y 7 se hacen una sola vez.

### Imprimir sin el cuadro de diálogo (opcional)

Si quiere que el ticket salga directo, sin elegir impresora cada vez:

1. Ponga la impresora térmica como **predeterminada** en Windows.
2. Cree un acceso directo de Chrome para el sistema con estos parámetros:
   `"C:\Program Files\Google\Chrome\Application\chrome.exe" --kiosk-printing --app=http://localhost:8080`
3. Con ese acceso directo, *Imprimir* envía el ticket directo a la impresora predeterminada.

Esto afecta todas las impresiones hechas desde ese acceso directo de Chrome. Úselo solo en la computadora de caja.

## Si algo sale mal

| Síntoma | Causa probable | Qué hacer |
|---------|----------------|-----------|
| El ticket sale cortado a la derecha | Ancho configurado mayor que el papel | Elija 58 mm en Configuración, o el tamaño correcto en el controlador |
| Sale muy pequeño | Escala "Ajustar a la página" | Escala 100 % / Predeterminada |
| Imprime fecha, URL o título | Encabezados del navegador activos | Desmarque "Encabezados y pies de página" |
| Avanza mucho papel en blanco | Tamaño de papel fijo largo en el controlador | Use "Roll paper" o el tamaño de rollo continuo |
| No aparece la impresora | Controlador no instalado | Instale el controlador del fabricante |
| Logo borroso o negro | Logo con colores o degradados | Suba un logo en blanco y negro de alto contraste |

## Lo que no está implementado

- **Impresión directa ESC/POS** por un servicio local en la computadora de caja (por ejemplo, para abrir
  el cajón de dinero o imprimir sin el navegador). Requiere conocer el modelo, instalar un programa
  local con autenticación y probarlo con el equipo. No se incluyó para no inventar una conexión no probada.
- **Bluetooth** desde el navegador.

## Registro de la prueba real (complételo)

| Fecha | Modelo de impresora | Papel | Sistema operativo / navegador | Resultado | Responsable |
|-------|---------------------|-------|-------------------------------|-----------|-------------|
|       |                     |       |                               | Pendiente |             |

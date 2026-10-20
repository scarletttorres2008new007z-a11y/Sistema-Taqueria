# Facturación electrónica (DTE): estado y pasos pendientes

> **Estado: NO integrada.** El sistema emite solo **comprobantes internos de venta**. No genera, firma ni
> transmite Documentos Tributarios Electrónicos (DTE) al Ministerio de Hacienda de El Salvador, y no
> muestra ningún documento como "aceptado por Hacienda". Cada comprobante lleva esta leyenda:
>
> *COMPROBANTE INTERNO - NO ES UN DOCUMENTO TRIBUTARIO ELECTRÓNICO NI SUSTITUYE A LA FACTURA AUTORIZADA POR EL MINISTERIO DE HACIENDA*

Este documento explica qué haría falta para integrarla. **Verifique cada requisito en el portal oficial de
la Dirección General de Impuestos Internos (DGII) del Ministerio de Hacienda antes de implementarlo**: las
normas, esquemas y plazos cambian, y lo de abajo se basa en información pública general, no en la normativa
oficial vigente revisada línea por línea.

## Comprobante interno vs. documento tributario

| | Comprobante interno (lo que hace el sistema) | DTE (factura electrónica) |
|---|---|---|
| Para qué sirve | Control interno: qué se vendió, cómo se pagó, reimpresión | Documento con validez tributaria |
| Numeración | CI-00000001 (consecutiva del sistema) | Número de control y código de generación definidos por Hacienda |
| Firma electrónica | No | Sí, con certificado autorizado |
| Validación | Ninguna externa | Hacienda recibe el documento y devuelve un sello de recepción |
| Nombre en el sistema | "Comprobante interno de venta" | No existe en el sistema |

## Proceso general para ser emisor de DTE

Según información pública ([Estrategia y Negocios](https://www.revistaeyn.com/finanzas/facturacion-electronica-se-vuelve-una-realidad-en-el-salvador-EE14548149), [Sovos](https://sovos.com/es/iva/reglas-fiscales/factura-electrnica-en-el-salvador/), [Edicom](https://edicomgroup.com/es/factura-electronica/el-salvador)), el proceso tiene estas etapas:

1. Solicitud en línea al Ministerio de Hacienda para ingresar como emisor.
2. Obtención del certificado de firma electrónica para el ambiente de pruebas.
3. Pruebas mínimas de emisión en el ambiente de pruebas.
4. Autorización como emisor.
5. Certificado de firma electrónica para el ambiente productivo.
6. Inicio de operaciones.

Flujo técnico de cada documento (resumen):
1. El sistema genera el documento en **JSON** con la estructura (esquema) oficial del tipo de DTE.
2. Lo **firma** con el certificado del emisor.
3. Lo **transmite** a la API de recepción de Hacienda con las credenciales del emisor.
4. Hacienda valida y, si es correcto, devuelve un **sello de recepción**. Solo entonces el documento es válido.
5. Se entrega al cliente (impreso o por correo), con los datos y código que exija la normativa, y se conserva
   durante el plazo legal. Las fuentes consultadas no coinciden en el plazo, así que debe confirmarlo en la normativa vigente.

Tipos de DTE más comunes en un restaurante (confirmar en la normativa): factura a consumidor final y
comprobante de crédito fiscal para clientes contribuyentes; también existen notas de crédito y débito,
y el procedimiento de **invalidación** de documentos.

## Lo que el negocio debe obtener (el sistema no puede inventarlo)

- NIT, NRC, nombre o razón social y actividad económica **reales** del contribuyente.
- Autorización de Hacienda como emisor de DTE.
- Credenciales de la API de recepción (pruebas y producción).
- Certificado de firma electrónica y la forma de firmar (servicio o librería autorizada).
- Esquemas JSON oficiales vigentes de cada tipo de documento.
- Aprobación de las pruebas en el ambiente de pruebas.

La pantalla **Configuración → Facturación electrónica** muestra esta lista de pendientes.

## Qué ya prepara el sistema

- Tabla `documentos_tributarios` en la base de datos para guardar, por venta: tipo de DTE, número de control,
  código de generación, JSON enviado, respuesta y sello de recepción, estado. **Hoy siempre está vacía.**
  Una prueba automática lo verifica.
- Datos del negocio (NIT, NRC, giro, dirección) en Configuración, copiados en cada comprobante.
- Datos del cliente en la venta (nombre, documento, dirección) para el crédito fiscal.
- IVA calculado y guardado en cada venta (precios con IVA incluido; tasa configurable, 13 % por defecto).

## Cómo integrarlo (trabajo futuro)

1. Crear un servicio `FacturacionElectronicaService` que, a partir de una venta pagada, arme el JSON según
   el esquema oficial y lo valide contra el esquema antes de enviarlo.
2. Firmar con el certificado del emisor (guardado fuera del código; ruta y contraseña por variables de entorno).
3. Enviar al ambiente de **pruebas** primero; guardar la respuesta completa en `documentos_tributarios`.
4. Marcar el documento como válido **solo** si la respuesta trae sello de recepción verificable. En cualquier
   otro caso dejarlo como pendiente o rechazado, con el motivo, y permitir el reintento.
5. Implementar la invalidación según el procedimiento oficial.
6. Pasar a producción solo con la autorización de Hacienda y las pruebas aprobadas.

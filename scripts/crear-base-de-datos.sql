-- Crea el usuario y las bases de datos del sistema.
-- Ejecútelo una sola vez como administrador de PostgreSQL, indicando la contraseña que desea:
--   psql -U postgres -v clave="SU_CONTRASEÑA_SEGURA" -f scripts/crear-base-de-datos.sql
-- Use la misma contraseña en DB_PASSWORD del archivo .env
-- Las tablas las crea el sistema automáticamente la primera vez que se inicia (Flyway).

CREATE ROLE taqueria LOGIN PASSWORD :'clave';
CREATE DATABASE taqueria OWNER taqueria ENCODING 'UTF8' TEMPLATE template0;
-- Base aparte para las pruebas automáticas (se borra en cada ejecución de pruebas)
CREATE DATABASE taqueria_test OWNER taqueria ENCODING 'UTF8' TEMPLATE template0;

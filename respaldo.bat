@echo off
REM Respaldo de la base de datos y de los archivos subidos (Windows).
REM Requiere pg_dump (viene con PostgreSQL). Si no se encuentra, agregue la carpeta bin de PostgreSQL al PATH,
REM por ejemplo: C:\Program Files\PostgreSQL\16\bin
setlocal
cd /d "%~dp0"
for /f "usebackq eol=# tokens=1* delims==" %%a in (".env") do set "%%a=%%b"
if "%DB_USER%"=="" set DB_USER=taqueria
if "%UPLOADS_DIR%"=="" set UPLOADS_DIR=uploads
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmm"') do set FECHA=%%i
if not exist respaldos mkdir respaldos
set PGPASSWORD=%DB_PASSWORD%
pg_dump -h localhost -U %DB_USER% -F c -f "respaldos\taqueria-%FECHA%.dump" taqueria
if errorlevel 1 (
  echo El respaldo de la base de datos FALLO. Revise que PostgreSQL este encendido y la contrasena en .env.
  pause
  exit /b 1
)
if exist "%UPLOADS_DIR%" powershell -NoProfile -Command "Compress-Archive -Path '%UPLOADS_DIR%\*' -DestinationPath 'respaldos\archivos-%FECHA%.zip' -Force"
echo Respaldo guardado en la carpeta respaldos: taqueria-%FECHA%.dump
echo Copie la carpeta respaldos a una memoria USB o a la nube con regularidad.
pause

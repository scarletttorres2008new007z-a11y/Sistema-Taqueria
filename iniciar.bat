@echo off
REM Inicia el sistema de la taqueria en Windows.
REM Requisitos: Java 17 o superior, PostgreSQL con la base creada y el archivo .env completo.
setlocal
cd /d "%~dp0"
if not exist ".env" (
  echo No existe el archivo .env. Copie .env.example como .env y escriba la contrasena de la base de datos.
  pause
  exit /b 1
)
for /f "usebackq eol=# tokens=1* delims==" %%a in (".env") do set "%%a=%%b"
if "%PORT%"=="" set PORT=8080
if not exist "target\taqueria.jar" (
  echo Compilando el sistema por primera vez. Puede tardar varios minutos...
  call mvnw.cmd -q -DskipTests package
  if errorlevel 1 (
    echo No se pudo compilar. Revise que Java 17 o superior este instalado.
    pause
    exit /b 1
  )
)
echo Iniciando el sistema. Abra http://localhost:%PORT% en el navegador.
echo Para detenerlo cierre esta ventana o presione Ctrl+C.
java -jar target\taqueria.jar
pause

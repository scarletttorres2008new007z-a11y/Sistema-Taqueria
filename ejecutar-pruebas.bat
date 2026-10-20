@echo off
REM Ejecuta las pruebas automaticas contra la base taqueria_test (se borra y se vuelve a crear).
setlocal
cd /d "%~dp0"
for /f "usebackq eol=# tokens=1* delims==" %%a in (".env") do set "%%a=%%b"
call mvnw.cmd test
pause

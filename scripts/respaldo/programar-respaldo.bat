@echo off
rem Ejecuta programar-respaldo.ps1 con los mismos parámetros. Ver la ayuda: powershell Get-Help "%~dp0programar-respaldo.ps1" -Detailed
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0programar-respaldo.ps1" %*
exit /b %ERRORLEVEL%

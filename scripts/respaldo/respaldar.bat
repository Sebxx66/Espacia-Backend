@echo off
rem Ejecuta respaldar.ps1 con los mismos parámetros. Ver la ayuda: powershell Get-Help "%~dp0respaldar.ps1" -Detailed
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0respaldar.ps1" %*
exit /b %ERRORLEVEL%

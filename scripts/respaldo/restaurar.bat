@echo off
rem Ejecuta restaurar.ps1 con los mismos parámetros. Ver la ayuda: powershell Get-Help "%~dp0restaurar.ps1" -Detailed
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0restaurar.ps1" %*
exit /b %ERRORLEVEL%

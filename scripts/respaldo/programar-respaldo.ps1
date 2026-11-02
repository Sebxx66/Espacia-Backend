<#
.SYNOPSIS
  Programa el respaldo diario de Espacia en el Programador de tareas de Windows.
.DESCRIPTION
  Crea (o reemplaza) la tarea "Espacia - respaldo diario" para el usuario actual, sin permisos de administrador.
  Se ejecuta a la hora indicada; si el equipo estaba apagado, en cuanto se encienda. Nunca hace más de un
  respaldo por día. Para quitarla: programar-respaldo.bat -Quitar
.EXAMPLE
  programar-respaldo.bat -Hora 13:00 -Carpeta C:\Espacia\respaldos -PgBin C:\Espacia\pgsql\bin
#>
param(
    [string]$Hora = "13:00",
    [string]$Carpeta = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) "respaldos"),
    [int]$Dias = 14,
    [string]$PgBin = $env:PG_BIN,
    [string]$Base = "bibliospace_db",
    [string]$Usuario = "postgres",
    [int]$Puerto = 5432,
    [switch]$Quitar
)
$ErrorActionPreference = "Stop"
$nombre = "Espacia - respaldo diario"

if ($Quitar) {
    Unregister-ScheduledTask -TaskName $nombre -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host "Tarea quitada."
    exit 0
}

$script = Join-Path $PSScriptRoot "respaldar.ps1"
$argumentos = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$script`" -UnoPorDia " +
    "-Carpeta `"$Carpeta`" -Dias $Dias -Base $Base -Usuario $Usuario -Puerto $Puerto"
if ($PgBin) { $argumentos += " -PgBin `"$PgBin`"" }

$accion = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $argumentos
$disparador = New-ScheduledTaskTrigger -Daily -At $Hora
$ajustes = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Hours 1)
Register-ScheduledTask -TaskName $nombre -Action $accion -Trigger $disparador -Settings $ajustes `
    -Description "Copia de seguridad diaria de la base de datos de Espacia" -Force | Out-Null
Write-Host "Listo: la tarea `"$nombre`" respaldará la base todos los días a las $Hora (o al encender el equipo si estaba apagado)."
Write-Host "Respaldos en: $Carpeta"

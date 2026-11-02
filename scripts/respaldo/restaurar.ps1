<#
.SYNOPSIS
  Restaura un respaldo de Espacia en Windows.
.DESCRIPTION
  -BaseNueva NOMBRE  carga el respaldo en una base nueva, para revisarlo o probarlo.
  -Reemplazar        reemplaza la base de Espacia. Antes hace un respaldo del estado actual.
                     Detén el backend antes. Pide escribir RESTAURAR para confirmar (o usa -Confirmado).
.EXAMPLE
  restaurar.bat -Archivo C:\Espacia\respaldos\espacia-2026-11-02_030000.dump -BaseNueva prueba_restauracion
#>
param(
    [Parameter(Mandatory = $true)][string]$Archivo,
    [string]$BaseNueva,
    [switch]$Reemplazar,
    [switch]$Confirmado,
    [string]$Carpeta = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) "respaldos"),
    [string]$PgBin = $env:PG_BIN,
    [string]$Servidor = $(if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }),
    [int]$Puerto = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 5432 }),
    [string]$Base = $(if ($env:DB_NAME) { $env:DB_NAME } else { "bibliospace_db" }),
    [string]$Usuario = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "postgres" })
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "comun.ps1")

function Fallar([string]$Mensaje) { Write-Host $Mensaje -ForegroundColor Red; exit 1 }

if (-not (Test-Path $Archivo)) { Fallar "No existe el archivo $Archivo" }
if ([bool]$BaseNueva -eq [bool]$Reemplazar) { Fallar "Indica -BaseNueva NOMBRE o -Reemplazar (uno de los dos)." }
$pgRestore = Get-PgTool $PgBin "pg_restore"
$conexion = @("-h", $Servidor, "-p", "$Puerto", "-U", $Usuario, "-w")

$r = Invoke-PgTool $pgRestore @("--list", $Archivo)
if ($r.Codigo -ne 0) { Fallar "El archivo no es un respaldo válido de pg_dump. $($r.Errores.Trim())" }

if ($Reemplazar) {
    if (-not $Confirmado) {
        Write-Host "Esto reemplaza TODOS los datos de $Base por los de $(Split-Path -Leaf $Archivo)." -ForegroundColor Yellow
        if ((Read-Host "Escribe RESTAURAR para continuar") -cne "RESTAURAR") { Fallar "Cancelado." }
    }
    Write-Host "Respaldando el estado actual antes de restaurar..."
    & (Join-Path $PSScriptRoot "respaldar.ps1") -Carpeta $Carpeta -PgBin $PgBin -Servidor $Servidor -Puerto $Puerto -Base $Base -Usuario $Usuario
    if ($LASTEXITCODE -ne 0) { Fallar "No se pudo respaldar el estado actual; no se restauró nada." }
    # Una sola transacción: si algo falla, la base queda como estaba
    $r = Invoke-PgTool $pgRestore ($conexion + @("-d", $Base, "--clean", "--if-exists", "--no-owner", "--no-privileges", "--single-transaction", $Archivo))
    if ($r.Codigo -ne 0) { Fallar "La restauración falló y la base quedó como estaba. $($r.Errores.Trim())" }
    Write-Host "Listo: $Base quedó como en $(Split-Path -Leaf $Archivo). Ya puedes iniciar el backend." -ForegroundColor Green
    exit 0
}

if ($BaseNueva -notmatch '^[A-Za-z0-9_]+$') { Fallar "Nombre de base no válido: $BaseNueva (usa letras, números y _)." }
if ($BaseNueva -eq $Base) { Fallar "Para reemplazar la base de Espacia usa -Reemplazar." }
$psql = Get-PgTool $PgBin "psql"
$r = Invoke-PgTool $psql ($conexion + @("-d", "postgres", "-tAc", "select 1 from pg_database where datname = '$BaseNueva'"))
if ($r.Codigo -ne 0) { Fallar "No se pudo conectar a PostgreSQL. $($r.Errores.Trim())" }
if ($r.Salida.Trim() -eq "1") { Fallar "La base $BaseNueva ya existe. Elige otro nombre o bórrala primero." }
$r = Invoke-PgTool (Get-PgTool $PgBin "createdb") ($conexion + @($BaseNueva))
if ($r.Codigo -ne 0) { Fallar "No se pudo crear la base $BaseNueva. $($r.Errores.Trim())" }
$r = Invoke-PgTool $pgRestore ($conexion + @("-d", $BaseNueva, "--no-owner", "--no-privileges", "--single-transaction", $Archivo))
if ($r.Codigo -ne 0) { Fallar "No se pudo cargar el respaldo en $BaseNueva. $($r.Errores.Trim())" }
Write-Host "Listo: $(Split-Path -Leaf $Archivo) quedó cargado en la base $BaseNueva." -ForegroundColor Green
exit 0

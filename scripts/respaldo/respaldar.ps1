<#
.SYNOPSIS
  Copia de seguridad de la base de datos de Espacia en Windows.
.DESCRIPTION
  Crea un archivo comprimido con toda la base (formato personalizado de pg_dump), comprueba que se puede
  leer, lo anota en respaldos.log y borra los respaldos con más de -Dias días.
  La contraseña de la base, si hace falta, se toma de DB_PASSWORD o PGPASSWORD.
.EXAMPLE
  respaldar.bat -Carpeta C:\Espacia\respaldos -PgBin C:\Espacia\pgsql\bin -UnoPorDia
#>
param(
    [string]$Carpeta = (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) "respaldos"),
    [int]$Dias = 14,
    # No hace nada si ya hay un respaldo de hoy (para ejecutarlo al iniciar Espacia)
    [switch]$UnoPorDia,
    [string]$PgBin = $env:PG_BIN,
    [string]$Servidor = $(if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }),
    [int]$Puerto = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 5432 }),
    [string]$Base = $(if ($env:DB_NAME) { $env:DB_NAME } else { "bibliospace_db" }),
    [string]$Usuario = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "postgres" })
)
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "comun.ps1")

if ($Dias -lt 1) { throw "-Dias debe ser mayor que 0." }
New-Item -ItemType Directory -Force -Path $Carpeta | Out-Null

$hoy = Get-Date -Format "yyyy-MM-dd"
if ($UnoPorDia -and (Get-ChildItem -Path $Carpeta -Filter "espacia-${hoy}_*.dump" -File -ErrorAction SilentlyContinue)) {
    Write-Host "Ya hay un respaldo de hoy."
    exit 0
}

# Nunca se sobrescribe un respaldo: si ya existe uno con el mismo nombre, se numera
$nombre = "espacia-" + (Get-Date -Format "yyyy-MM-dd_HHmmss")
$archivo = Join-Path $Carpeta "$nombre.dump"
$n = 2
while (Test-Path $archivo) { $archivo = Join-Path $Carpeta "$nombre-$n.dump"; $n++ }
$parcial = "$archivo.parcial"

try {
    $pgDump = Get-PgTool $PgBin "pg_dump"
    $pgRestore = Get-PgTool $PgBin "pg_restore"
} catch {
    Write-Registro $Carpeta "ERROR: $($_.Exception.Message)"
    exit 1
}

$r = Invoke-PgTool $pgDump @("-h", $Servidor, "-p", "$Puerto", "-U", $Usuario, "-d", $Base, "-w",
    "--format=custom", "--compress=6", "--no-owner", "--no-privileges", "-f", $parcial)
if ($r.Codigo -ne 0) {
    Remove-Item -Force -ErrorAction SilentlyContinue $parcial
    Write-Registro $Carpeta "ERROR: no se pudo respaldar $Base en ${Servidor}:$Puerto. $($r.Errores.Trim())"
    exit 1
}
$r = Invoke-PgTool $pgRestore @("--list", $parcial)
if ($r.Codigo -ne 0) {
    Remove-Item -Force -ErrorAction SilentlyContinue $parcial
    Write-Registro $Carpeta "ERROR: el respaldo quedó ilegible y se descartó. $($r.Errores.Trim())"
    exit 1
}
Move-Item -Path $parcial -Destination $archivo
$kb = [math]::Ceiling((Get-Item $archivo).Length / 1KB)
Write-Registro $Carpeta "OK $(Split-Path -Leaf $archivo) ($kb KB)"

# Solo después de un respaldo correcto se borran los viejos: siempre queda al menos el de hoy
$limite = (Get-Date).AddDays(-$Dias)
Get-ChildItem -Path $Carpeta -Filter "espacia-*.dump" -File | Where-Object { $_.LastWriteTime -lt $limite } | ForEach-Object {
    Remove-Item -Force $_.FullName
    Write-Registro $Carpeta "Borrado por tener más de $Dias días: $($_.Name)"
}
exit 0

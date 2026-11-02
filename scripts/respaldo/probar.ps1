# Prueba de punta a punta de los respaldos en Windows (la ejecuta la integración continua contra un PostgreSQL real).
param([string]$PgBin = $env:PGBIN)
$ErrorActionPreference = "Stop"
$dir = $PSScriptRoot
$carpeta = Join-Path ([System.IO.Path]::GetTempPath()) ("respaldos-" + [guid]::NewGuid().ToString("N").Substring(0, 8))
$psql = Join-Path $PgBin "psql.exe"
function Q([string]$Base, [string]$Sql) { (& $psql -h localhost -U postgres -w -d $Base -tAc $Sql | Out-String).Trim() }
function Falla([string]$Texto) { Write-Host "FALLA: $Texto" -ForegroundColor Red; exit 1 }
function Respaldos { @(Get-ChildItem -Path $carpeta -Filter "espacia-*.dump" -File) }
$comunes = @{ Carpeta = $carpeta; PgBin = $PgBin }

Q "bibliospace_db" "drop table if exists prueba_respaldo; create table prueba_respaldo(id serial primary key, nombre text not null); insert into prueba_respaldo(nombre) select 'fila ' || g from generate_series(1, 500) g;" | Out-Null

& "$dir\respaldar.ps1" @comunes
if ($LASTEXITCODE -ne 0) { Falla "respaldar.ps1 terminó con código $LASTEXITCODE" }
if ((Respaldos).Count -ne 1) { Falla "se esperaba 1 respaldo y hay $((Respaldos).Count)" }

& "$dir\respaldar.ps1" @comunes -UnoPorDia
if ((Respaldos).Count -ne 1) { Falla "-UnoPorDia hizo un segundo respaldo" }

$archivo = (Respaldos)[0].FullName
& "$dir\restaurar.ps1" @comunes -Archivo $archivo -BaseNueva prueba_restauracion
if ($LASTEXITCODE -ne 0) { Falla "no se pudo restaurar en una base nueva" }
if ((Q "prueba_restauracion" "select count(*) from prueba_respaldo") -ne "500") { Falla "la base restaurada no tiene las 500 filas" }

# Retención: uno de hace 20 días se borra; uno de hace 3 días se conserva
$viejo = Join-Path $carpeta "espacia-2000-01-01_000000.dump"
$reciente = Join-Path $carpeta "espacia-2000-01-02_000000.dump"
Copy-Item $archivo $viejo; (Get-Item $viejo).LastWriteTime = (Get-Date).AddDays(-20)
Copy-Item $archivo $reciente; (Get-Item $reciente).LastWriteTime = (Get-Date).AddDays(-3)
& "$dir\respaldar.ps1" @comunes
if (Test-Path $viejo) { Falla "no se borró el respaldo viejo" }
if (-not (Test-Path $reciente)) { Falla "se borró un respaldo reciente" }
Remove-Item $reciente

# Restauración completa: deshace un cambio posterior y deja un respaldo previo de seguridad
Q "bibliospace_db" "delete from prueba_respaldo where id > 100" | Out-Null
$antes = (Respaldos).Count
& "$dir\restaurar.ps1" @comunes -Archivo $archivo -Reemplazar -Confirmado
if ($LASTEXITCODE -ne 0) { Falla "la restauración completa falló" }
if ((Q "bibliospace_db" "select count(*) from prueba_respaldo") -ne "500") { Falla "la restauración completa no recuperó las filas" }
if ((Respaldos).Count -ne $antes + 1) { Falla "no se hizo el respaldo previo a restaurar" }
if (-not (Select-String -Path (Join-Path $carpeta "respaldos.log") -Pattern " OK espacia-" -Quiet)) { Falla "el log no registra los respaldos" }

# Un error de conexión queda en el log y devuelve código distinto de 0
& "$dir\respaldar.ps1" @comunes -Puerto 5999
if ($LASTEXITCODE -eq 0) { Falla "un respaldo fallido devolvió código 0" }
if (-not (Select-String -Path (Join-Path $carpeta "respaldos.log") -Pattern "ERROR" -Quiet)) { Falla "el error no quedó en el log" }

# Con -OmitirSiApagado, un servidor apagado no es un error ni deja respaldo
$antes = (Respaldos).Count
& "$dir\respaldar.ps1" @comunes -Puerto 5999 -OmitirSiApagado
if ($LASTEXITCODE -ne 0) { Falla "-OmitirSiApagado devolvió error con el servidor apagado" }
if ((Respaldos).Count -ne $antes) { Falla "-OmitirSiApagado creó un respaldo" }

Write-Host "Respaldos en Windows: todas las pruebas pasaron." -ForegroundColor Green
exit 0

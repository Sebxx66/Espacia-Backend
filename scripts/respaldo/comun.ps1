# Funciones compartidas por los scripts de respaldo de Windows (no se ejecuta solo).

# Ruta de un programa de PostgreSQL: en la carpeta PgBin si se indicó, si no el que esté en el PATH
function Get-PgTool([string]$PgBin, [string]$Nombre) {
    if ($PgBin) {
        $ruta = Join-Path $PgBin "$Nombre.exe"
        if (-not (Test-Path $ruta)) { throw "No se encontró $ruta. Revisa el parámetro -PgBin (carpeta bin de PostgreSQL)." }
        return $ruta
    }
    $cmd = Get-Command $Nombre -ErrorAction SilentlyContinue
    if (-not $cmd) { throw "No se encontró $Nombre. Indica la carpeta bin de PostgreSQL con -PgBin o en la variable PG_BIN." }
    return $cmd.Source
}

# Ejecuta un programa y devuelve @{ Codigo; Salida; Errores } sin que PowerShell trate su salida de error como excepción
function Invoke-PgTool([string]$Programa, [string[]]$Argumentos) {
    $salida = [System.IO.Path]::GetTempFileName()
    $errores = [System.IO.Path]::GetTempFileName()
    try {
        $citados = $Argumentos | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }
        $p = Start-Process -FilePath $Programa -ArgumentList $citados -NoNewWindow -Wait -PassThru `
            -RedirectStandardOutput $salida -RedirectStandardError $errores
        return @{
            Codigo  = $p.ExitCode
            Salida  = "" + (Get-Content -Raw -Encoding UTF8 $salida)
            Errores = "" + (Get-Content -Raw -Encoding UTF8 $errores)
        }
    } finally {
        Remove-Item -Force -ErrorAction SilentlyContinue $salida, $errores
    }
}

function Write-Registro([string]$Carpeta, [string]$Texto) {
    $linea = "{0} {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Texto
    Write-Host $linea
    Add-Content -Path (Join-Path $Carpeta "respaldos.log") -Value $linea -Encoding UTF8
}

# La contraseña puede venir en DB_PASSWORD (como en el backend) o en PGPASSWORD
if ($env:DB_PASSWORD -and -not $env:PGPASSWORD) { $env:PGPASSWORD = $env:DB_PASSWORD }

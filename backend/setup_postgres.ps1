# Cree la database PostgreSQL ytfamily (user postgres / password root).
# Usage:
#   .\backend\setup_postgres.ps1
# Optionnel:
#   .\backend\setup_postgres.ps1 -PostgresPassword "root" -AppDb "ytfamily"

param(
    [string]$PostgresPassword = "root",
    [string]$PostgresUser = "postgres",
    [string]$HostName = "127.0.0.1",
    [int]$Port = 5432,
    [string]$AppDb = "ytfamily"
)

$ErrorActionPreference = "Stop"
$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
if (-not (Test-Path $psql)) {
    $found = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $found) { throw "psql.exe introuvable. Verifie l'installation PostgreSQL." }
    $psql = $found
}

$env:PGPASSWORD = $PostgresPassword
$env:PGCLIENTENCODING = "UTF8"

Write-Host "Connexion $PostgresUser@$HostName:$Port ..."
& $psql -U $PostgresUser -h $HostName -p $Port -d postgres -v ON_ERROR_STOP=1 -c "SELECT current_user;"
if ($LASTEXITCODE -ne 0) { throw "Connexion postgres echouee (mot de passe?)." }

$dbExists = & $psql -U $PostgresUser -h $HostName -p $Port -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$AppDb'"
if ($dbExists -ne "1") {
    & $psql -U $PostgresUser -h $HostName -p $Port -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $AppDb OWNER $PostgresUser;"
    if ($LASTEXITCODE -ne 0) { throw "CREATE DATABASE echoue." }
    Write-Host "Database $AppDb creee."
} else {
    Write-Host "Database $AppDb existe deja."
}

& $psql -U $PostgresUser -h $HostName -p $Port -d $AppDb -v ON_ERROR_STOP=1 -c "SELECT current_database(), current_user;"
if ($LASTEXITCODE -ne 0) { throw "Connexion a $AppDb echouee." }

Write-Host ""
Write-Host "OK. DATABASE_URL:"
Write-Host "postgresql+psycopg://${PostgresUser}:${PostgresPassword}@${HostName}:${Port}/${AppDb}"
Write-Host "Ensuite: cd backend ; .\\run_local.ps1"

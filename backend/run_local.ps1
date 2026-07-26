# Lance l'API + site parent en local (PostgreSQL)
# Prérequis: Postgres local avec user/db ytfamily (voir README)
# Usage: depuis youtube-channel\backend
#   .\run_local.ps1

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path .\.venv\Scripts\python.exe)) {
    Write-Host "Creation du venv..."
    python -m venv .venv
    .\.venv\Scripts\pip install -r requirements.txt
}

Write-Host "Demarrage sur http://127.0.0.1:8000 (PostgreSQL)"
Write-Host "Ouvre http://127.0.0.1:8000/ pour le portail parent"
.\.venv\Scripts\python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000

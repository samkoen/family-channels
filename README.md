# YouTube Family Channels

App Android **enfant uniquement** + site **parent** (web) + API **FastAPI** / **PostgreSQL**.

L’enfant ne voit que les chaînes approuvées pour son profil. Le parent configure tout sur le navigateur (code famille + PIN). Aucun mode parent dans l’app.

## Structure

- `android/` — app Kotlin multi-modules (Compose)
- `backend/` — FastAPI, Jinja2 (portail parent), API enfant
- `docs/` — notes complémentaires
- `PLAN.md` — plan produit / technique
- `docker-compose.yml` — Postgres + API (optionnel)

## Prérequis

- **PostgreSQL** local (service Windows ou Docker)
- Clé [YouTube Data API v3](https://console.cloud.google.com/)
- Android Studio (Ladybug+) pour l’app
- JDK 17

## Backend (local) — PostgreSQL

### 1. Préparer Postgres

Même style que tes autres projets (`postgres` / `root`), base `ytfamily` :

```powershell
cd backend
.\setup_postgres.ps1
```

`backend/.env` attend :

```env
DATABASE_URL=postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily
```

### 2. Configurer et lancer

```powershell
cd backend
# Vérifier .env :
# DATABASE_URL=postgresql+psycopg://ytfamily:ytfamily@localhost:5432/ytfamily
# YOUTUBE_API_KEY=...
.\run_local.ps1
```

- Site parent : http://127.0.0.1:8000/
- Site enfant : http://127.0.0.1:8000/watch
- Santé API : http://127.0.0.1:8000/health

### Option Docker (Postgres + API)

```powershell
copy .env.example .env
# Éditer YOUTUBE_API_KEY=...
docker compose up --build
```

### Tests backend

Postgres local doit être prêt (`setup_postgres.ps1` une fois).

```powershell
cd backend
.\.venv\Scripts\python -m pytest -q
```

## Portail parent (résumé)

1. Créer une famille (PIN 4–6 chiffres) → noter le **code famille**
2. Se connecter avec code + PIN
3. Ajouter des profils enfants + limite minutes/jour
4. Ajouter des chaînes YouTube (URL ou `@handle`) **par enfant**
5. **Éditer** une chaîne pour ajouter des filtres (OU sur le titre)

## App Android

1. Ouvrir `android/` dans Android Studio
2. Sync Gradle
3. Lancer sur émulateur (API pointe vers `10.0.2.2:8000` = localhost machine)
4. Saisir le code famille → choisir un profil → chaînes → vidéos

### Modules

| Module | Rôle |
|--------|------|
| `:core:domain` | models + use cases (+ tests unitaires) |
| `:core:data` | Retrofit + DataStore |
| `:core:ui` | thème neutre + i18n FR/EN/HE |
| `:feature:*` | join, home, videos, player, quota |
| `:app` | navigation |

## Déploiement (plus tard)

Pas Vercel pour le tout : Postgres + FastAPI sur Railway / Render / Fly.io ; app Android à part. Voir conversation / docs.

## Variables d’environnement

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | URL SQLAlchemy Postgres (`postgresql+psycopg://...`) |
| `SECRET_KEY` | signature cookies / tokens |
| `YOUTUBE_API_KEY` | clé API Google YouTube Data |
| `YOUTUBE_CACHE_TTL_SECONDS` | durée du cache listes vidéos (défaut 10800 = 3 h) |

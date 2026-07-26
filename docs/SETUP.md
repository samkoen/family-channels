# Documentation

## PostgreSQL local

Même convention que tes autres projets :

```env
DATABASE_URL=postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily
```

Créer la base une fois :

```powershell
cd backend
.\setup_postgres.ps1
```

SQLite n’est plus utilisé pour l’application.

## Cache YouTube

Les listes / recherches de vidéos sont mises en cache en Postgres (`video_caches`) pendant **3 heures** par défaut (`YOUTUBE_CACHE_TTL_SECONDS`).  
Le cache est invalidé quand le parent ajoute ou retire un filtre.

## Clé YouTube Data API

1. Google Cloud Console → créer un projet
2. Activer **YouTube Data API v3**
3. Créer une clé API (restreindre par IP en prod)
4. Mettre la clé dans `.env` → `YOUTUBE_API_KEY`

## Flux enfant

1. Parent crée la famille sur le web
2. Enfant saisit le code dans l’app
3. Choisit son profil
4. Ne voit que ses chaînes ; lecture bloquée si quota journalier épuisé

## Sécurité volontaire

- Pas d’écran parent dans l’app
- Pas de lien vers le site parent dans l’app
- Token enfant sans PIN
- Player limité aux `videoId` de la liste autorisée

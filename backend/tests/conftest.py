import os

# Postgres local obligatoire pour les tests d'intégration DB.
os.environ.setdefault(
    "DATABASE_URL",
    "postgresql+psycopg://postgres:root@127.0.0.1:5432/ytfamily",
)
os.environ.setdefault("SECRET_KEY", "test-secret")

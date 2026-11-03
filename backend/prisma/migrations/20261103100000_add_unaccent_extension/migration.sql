-- Recherche insensible aux accents (GET /search) : unaccent() retire les accents
-- avant la comparaison. Extension fournie avec PostgreSQL.
CREATE EXTENSION IF NOT EXISTS unaccent;

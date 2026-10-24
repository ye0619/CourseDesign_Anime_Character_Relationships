CREATE CONSTRAINT entity_uid IF NOT EXISTS FOR (n:Entity) REQUIRE n.uid IS UNIQUE;
CREATE INDEX anime_year IF NOT EXISTS FOR (n:Anime) ON (n.year);
CREATE INDEX anime_source IF NOT EXISTS FOR (n:Anime) ON (n.source);
CREATE INDEX participant_uid IF NOT EXISTS FOR ()-[r:PARTICIPATED_IN]-() ON (r.uid);
CREATE INDEX appearance_uid IF NOT EXISTS FOR ()-[r:APPEARS_IN]-() ON (r.uid);
CREATE INDEX voiced_uid IF NOT EXISTS FOR ()-[r:VOICED]-() ON (r.uid);
CREATE INDEX related_uid IF NOT EXISTS FOR ()-[r:RELATED_TO]-() ON (r.uid);
CREATE INDEX character_relation_uid IF NOT EXISTS FOR ()-[r:CHARACTER_RELATION]-() ON (r.uid);

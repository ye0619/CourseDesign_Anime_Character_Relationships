CREATE CONSTRAINT connection_character_uid IF NOT EXISTS FOR (n:ConnectionCharacter) REQUIRE n.canonicalUid IS UNIQUE;
CREATE CONSTRAINT connection_voice_actor_uid IF NOT EXISTS FOR (n:ConnectionVoiceActor) REQUIRE n.canonicalUid IS UNIQUE;
CREATE CONSTRAINT connection_revision_uid IF NOT EXISTS FOR (n:ConnectionRevision) REQUIRE n.uid IS UNIQUE;
CREATE CONSTRAINT connection_active_uid IF NOT EXISTS FOR (n:ConnectionActive) REQUIRE n.uid IS UNIQUE;
CREATE INDEX connection_voice_revision IF NOT EXISTS FOR ()-[r:CONNECTION_VOICE]-() ON (r.revision);
CREATE INDEX connection_story_revision IF NOT EXISTS FOR ()-[r:CONNECTION_STORY]-() ON (r.revision);

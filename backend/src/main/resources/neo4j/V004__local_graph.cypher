CREATE CONSTRAINT local_graph_workspace_uid IF NOT EXISTS FOR (n:LocalGraphWorkspace) REQUIRE n.uid IS UNIQUE;
CREATE CONSTRAINT local_graph_entity_uid IF NOT EXISTS FOR (n:LocalGraphEntity) REQUIRE n.uid IS UNIQUE;
CREATE CONSTRAINT local_graph_anchor_uid IF NOT EXISTS FOR (n:LocalGraphAnchor) REQUIRE n.uid IS UNIQUE;
CREATE CONSTRAINT local_graph_backup_uid IF NOT EXISTS FOR (n:LocalGraphBackup) REQUIRE n.uid IS UNIQUE;
CREATE INDEX local_graph_entity_work IF NOT EXISTS FOR (n:LocalGraphEntity) ON (n.workUid);
CREATE INDEX local_graph_anchor_work IF NOT EXISTS FOR (n:LocalGraphAnchor) ON (n.workUid);
CREATE INDEX local_graph_backup_work IF NOT EXISTS FOR (n:LocalGraphBackup) ON (n.workUid);
CREATE INDEX local_graph_link_work IF NOT EXISTS FOR ()-[r:LOCAL_GRAPH_LINK]-() ON (r.workUid);

CREATE CONSTRAINT evidence_import_batch_uid IF NOT EXISTS FOR (n:EvidenceImportBatch) REQUIRE n.uid IS UNIQUE;

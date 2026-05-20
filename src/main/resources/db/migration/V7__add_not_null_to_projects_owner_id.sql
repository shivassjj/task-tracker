DELETE FROM projects WHERE owner_id IS NULL;

ALTER TABLE projects
    ALTER COLUMN owner_id SET NOT NULL;
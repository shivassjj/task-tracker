CREATE TABLE tasks_tags (
    task_id BIGINT REFERENCES tasks(id) ON DELETE CASCADE,
    tag_id BIGINT REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, tag_id)
)
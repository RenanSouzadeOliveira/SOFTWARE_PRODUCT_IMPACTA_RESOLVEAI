-- SQL TRIM normalizes ASCII-space edges in valid legacy content. API writes also
-- remove ECMAScript Unicode edge whitespace; legacy Unicode content is preserved.
-- Invalid content is never truncated/deleted:
-- adding the constraint fails explicitly if an incompatible legacy row remains.
UPDATE comentarios
SET conteudo = TRIM(conteudo)
WHERE CHAR_LENGTH(TRIM(conteudo)) BETWEEN 1 AND 2000;

ALTER TABLE comentarios ADD CONSTRAINT ck_comentarios_conteudo_normalizado
    CHECK (conteudo = TRIM(conteudo) AND CHAR_LENGTH(conteudo) BETWEEN 1 AND 2000);

-- Replace the prefix-only index: reads always order by timestamp and id together.
DROP INDEX idx_comentarios_chamado_criado_em;
CREATE INDEX idx_comentarios_chamado_data_id ON comentarios (chamado_id, criado_em, id);

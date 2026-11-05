-- O índice V1 exige ordenar todos os chamados com a mesma data antes do LIMIT.
-- A ordem completa atende a listagem do solicitante sem sort e sem índice redundante.
CREATE INDEX idx_chamados_solicitante_criado_id ON chamados (solicitante_id, criado_em DESC, id DESC);
DROP INDEX idx_chamados_solicitante_criado_em;

-- Com atendente_id IS NULL antes da data, o plano da fila pode precisar ordenar todo o escopo.
-- Mantém status como igualdade e a ordem completa antes da coluna nullable.
CREATE INDEX idx_chamados_fila_ordem ON chamados (status, criado_em DESC, id DESC, atendente_id);
DROP INDEX idx_chamados_fila_criado_id;

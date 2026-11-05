-- Nova consulta global do ADMIN: sem igualdade em solicitante/status/atendente,
-- os índices V1/V4/V7 exigem scan e sort. EXPLAIN com 50 mil registros fictícios
-- confirmou leitura ordenada limitada a 20 linhas com este índice.
CREATE INDEX idx_chamados_criado_id ON chamados (criado_em DESC, id DESC);

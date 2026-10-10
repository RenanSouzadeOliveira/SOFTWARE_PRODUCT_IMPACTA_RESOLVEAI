-- A data de atribuicao de registros legados sem evidencia permanece desconhecida (NULL).
ALTER TABLE chamados ADD COLUMN atribuido_em TIMESTAMP WITH TIME ZONE;

UPDATE chamados
SET atribuido_em = (
    SELECT MIN(h.criado_em) FROM historico_chamados h
    WHERE h.chamado_id = chamados.id AND h.tipo_evento = 'ATRIBUICAO'
      AND h.atendente_novo_id = chamados.atendente_id
)
WHERE atendente_id IS NOT NULL;

-- Os indices anteriores por prioridade/status nao cobrem a ordenacao sem esses filtros.
CREATE INDEX idx_chamados_fila_criado_id ON chamados (status, atendente_id, criado_em DESC, id DESC);
CREATE INDEX idx_chamados_carteira_criado_id ON chamados (atendente_id, criado_em DESC, id DESC);

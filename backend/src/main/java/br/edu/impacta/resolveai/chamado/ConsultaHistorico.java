package br.edu.impacta.resolveai.chamado;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import br.edu.impacta.resolveai.chamado.dto.EventoHistoricoResponse;
import br.edu.impacta.resolveai.chamado.dto.UsuarioChamadoResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Uma consulta/snapshot para as duas fontes AC2; nenhum histórico derivado do estado atual. */
@Repository
public class ConsultaHistorico {
    private final JdbcTemplate jdbc;

    public ConsultaHistorico(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<EventoHistoricoResponse> listar(Long chamadoId) {
        return jdbc.query("""
            SELECT e.*, a.nome AS autor_nome, anterior.nome AS anterior_nome, novo.nome AS novo_nome
            FROM (
                SELECT 'STATUS' AS origem, s.id, s.chamado_id, 'STATUS' AS tipo_evento,
                       s.autor_id, s.criado_em, s.status_anterior AS valor_anterior,
                       s.status_novo AS valor_novo, CAST(NULL AS BIGINT) AS anterior_id,
                       CAST(NULL AS BIGINT) AS novo_id, s.observacao
                FROM historico_status s WHERE s.chamado_id = ?
                UNION ALL
                SELECT 'CHAMADO', h.id, h.chamado_id, h.tipo_evento, h.autor_id, h.criado_em,
                       CASE h.tipo_evento WHEN 'PRIORIDADE' THEN h.prioridade_anterior
                            WHEN 'ATRIBUICAO' THEN CAST(h.atendente_anterior_id AS VARCHAR(20))
                            ELSE h.status_anterior END,
                       CASE h.tipo_evento WHEN 'PRIORIDADE' THEN h.prioridade_nova
                            WHEN 'ATRIBUICAO' THEN CAST(h.atendente_novo_id AS VARCHAR(20))
                            ELSE h.status_novo END,
                       h.atendente_anterior_id, h.atendente_novo_id, h.justificativa
                FROM historico_chamados h WHERE h.chamado_id = ?
                  AND (h.tipo_evento <> 'STATUS' OR NOT EXISTS (
                      SELECT 1 FROM historico_status s
                      WHERE s.chamado_id = h.chamado_id AND s.autor_id = h.autor_id
                        AND s.criado_em = h.criado_em AND s.status_anterior = h.status_anterior
                        AND s.status_novo = h.status_novo
                        AND COALESCE(NULLIF(TRIM(s.observacao), ''), '') =
                            COALESCE(NULLIF(TRIM(h.justificativa), ''), '')
                  ))
            ) e
            JOIN usuarios a ON a.id = e.autor_id
            LEFT JOIN usuarios anterior ON anterior.id = e.anterior_id
            LEFT JOIN usuarios novo ON novo.id = e.novo_id
            ORDER BY e.criado_em DESC, e.origem DESC, e.id DESC
            """, (rs, row) -> new EventoHistoricoResponse(
                rs.getString("origem") + "-" + rs.getLong("id"), rs.getLong("chamado_id"),
                TipoEventoHistorico.valueOf(rs.getString("tipo_evento")),
                new UsuarioChamadoResponse(rs.getLong("autor_id"), rs.getString("autor_nome")),
                rs.getTimestamp("criado_em").toInstant(),
                valor(rs, "valor_anterior", "anterior_nome"), valor(rs, "valor_novo", "novo_nome"),
                rs.getString("observacao")), chamadoId, chamadoId);
    }

    private EventoHistoricoResponse.Valor valor(ResultSet rs, String codigo, String nome) throws SQLException {
        String value = rs.getString(codigo);
        return value == null ? null : new EventoHistoricoResponse.Valor(value, rs.getString(nome));
    }
}

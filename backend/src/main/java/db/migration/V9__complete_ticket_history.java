package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserva integralmente AC2. Proteção de escrita para PostgreSQL; H2 é apenas test double. */
public class V9__complete_ticket_history extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 20261105; }

    @Override public void migrate(Context context) throws Exception {
        try (var statement = context.getConnection().createStatement()) {
            // O índice de status já permite scan reverso por data/id. Acrescenta desempate ao geral.
            statement.execute("DROP INDEX idx_historico_chamado_criado_em");
            statement.execute("CREATE INDEX idx_historico_chamado_data_id ON historico_chamados (chamado_id, criado_em DESC, id DESC)");
            if (context.getConnection().getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
                statement.execute("""
                    CREATE FUNCTION impedir_mutacao_historico() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        RAISE EXCEPTION 'Historico de chamados e imutavel' USING ERRCODE = '23514';
                        RETURN NULL;
                    END;
                    $$
                    """);
                for (String table : new String[] {"historico_chamados", "historico_status"}) {
                    statement.execute("CREATE TRIGGER " + table + "_imutavel BEFORE UPDATE OR DELETE OR TRUNCATE ON "
                            + table + " FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_historico()");
                }
            }
        }
    }
}

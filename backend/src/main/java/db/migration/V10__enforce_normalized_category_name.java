package db.migration;

import java.sql.SQLException;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V10__enforce_normalized_category_name extends BaseJavaMigration {

    private static final String NOME_NORMALIZADO =
            "LOWER(REGEXP_REPLACE(REGEXP_REPLACE(nome, '^\\s+', ''), '\\s+$', ''))";

    @Override
    public Integer getChecksum() {
        return 20261121;
    }

    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        try (var statement = connection.createStatement()) {
            try (var duplicadas = statement.executeQuery("""
                    SELECT COUNT(*) FROM categorias GROUP BY %s HAVING COUNT(*) > 1
                    """.formatted(NOME_NORMALIZADO))) {
                if (duplicadas.next()) {
                    throw new SQLException(
                            "Existem categorias com nomes equivalentes. Revise os registros antes de reaplicar a migração.",
                            "23505");
                }
            }

            if ("PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                statement.execute("CREATE UNIQUE INDEX uk_categorias_nome_normalizado ON categorias ("
                        + NOME_NORMALIZADO + ")");
            } else {
                // H2 não aceita expressões em índices.
                statement.execute("ALTER TABLE categorias ADD COLUMN nome_normalizado VARCHAR(100) GENERATED ALWAYS AS ("
                        + NOME_NORMALIZADO + ")");
                statement.execute("ALTER TABLE categorias ADD CONSTRAINT uk_categorias_nome_normalizado "
                        + "UNIQUE (nome_normalizado)");
            }
        }
    }
}

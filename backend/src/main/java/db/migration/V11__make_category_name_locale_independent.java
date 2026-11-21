package db.migration;

import java.sql.SQLException;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V11__make_category_name_locale_independent extends BaseJavaMigration {

    // White_Space do Unicode, a mesma propriedade usada na entrada de Categoria.
    private static final String NOME_CANONICO = """
            normalize(lower(normalize(btrim(nome,
                U&'\\0009\\000A\\000B\\000C\\000D\\0020\\0085\\00A0\\1680\\2000\\2001\\2002\\2003\\2004\\2005\\2006\\2007\\2008\\2009\\200A\\2028\\2029\\202F\\205F\\3000'),
                NFC) COLLATE pg_catalog.pg_c_utf8), NFC) COLLATE pg_catalog."C"
            """.strip();

    @Override
    public Integer getChecksum() {
        return 2026112201;
    }

    @Override
    public void migrate(Context context) throws SQLException {
        var connection = context.getConnection();
        if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
            // H2 mantém o índice de teste da V10.
            return;
        }
        try (var statement = connection.createStatement()) {
            statement.execute("LOCK TABLE categorias IN ACCESS EXCLUSIVE MODE");
            try (var duplicadas = statement.executeQuery(
                    "SELECT 1 FROM categorias GROUP BY " + NOME_CANONICO + " HAVING COUNT(*) > 1 LIMIT 1")) {
                if (duplicadas.next()) {
                    throw new SQLException(
                            "Existem categorias com nomes Unicode equivalentes. Revise os registros antes de reaplicar a migração.",
                            "23505");
                }
            }
            statement.execute("DROP INDEX uk_categorias_nome_normalizado");
            statement.execute("CREATE UNIQUE INDEX uk_categorias_nome_normalizado ON categorias (("
                    + NOME_CANONICO + "))");
        }
    }
}

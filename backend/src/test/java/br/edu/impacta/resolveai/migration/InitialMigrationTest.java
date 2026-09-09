package br.edu.impacta.resolveai.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class InitialMigrationTest {

    @Test
    void shouldApplyInitialMigrationAndCreateDomainTables() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:initial_migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        MigrateResult migrationResult = flyway.migrate();

        assertThat(migrationResult.migrationsExecuted).isEqualTo(3);
        assertThat(flyway.info().applied()).hasSize(3);

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            Set<String> tables = new HashSet<>();
            try (ResultSet result = metadata.getTables(null, "public", "%", new String[] {"TABLE"})) {
                while (result.next()) {
                    tables.add(result.getString("TABLE_NAME").toLowerCase());
                }
            }
            assertThat(tables).contains(
                    "usuarios", "categorias", "chamados", "comentarios", "historico_chamados");

            assertColumnSize(metadata, "chamados", "titulo", 120);
            assertColumnSize(metadata, "chamados", "descricao", 2000);
            assertTicketIndexes(metadata);
        }
    }

    @Test
    void shouldNormalizeExistingEmailWhenUpgradingFromV1() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:upgrade_migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO usuarios (nome, email, senha_hash, perfil)
                    VALUES ('Pessoa existente', '  Pessoa@Example.COM  ', 'hash-ficticio', 'SOLICITANTE')
                    """);
        }

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = dataSource.getConnection();
                ResultSet result = connection.createStatement()
                        .executeQuery("SELECT email FROM usuarios")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("email")).isEqualTo("pessoa@example.com");
        }
    }

    private void assertColumnSize(DatabaseMetaData metadata, String table, String column, int expectedSize)
            throws SQLException {
        try (ResultSet result = metadata.getColumns(null, "public", table, column)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt("COLUMN_SIZE")).isEqualTo(expectedSize);
        }
    }

    private void assertTicketIndexes(DatabaseMetaData metadata) throws SQLException {
        Map<String, List<String>> columnsByIndex = new LinkedHashMap<>();
        Set<String> uniqueIndexes = new HashSet<>();
        try (ResultSet result = metadata.getIndexInfo(null, "public", "chamados", false, false)) {
            while (result.next()) {
                String indexName = result.getString("INDEX_NAME");
                String columnName = result.getString("COLUMN_NAME");
                if (indexName != null && columnName != null) {
                    columnsByIndex.computeIfAbsent(indexName.toLowerCase(), ignored -> new java.util.ArrayList<>())
                            .add(columnName.toLowerCase());
                    if (!result.getBoolean("NON_UNIQUE")) {
                        uniqueIndexes.add(indexName.toLowerCase());
                    }
                }
            }
        }

        assertThat(columnsByIndex).anySatisfy((name, columns) -> {
            assertThat(uniqueIndexes).contains(name);
            assertThat(columns).containsExactly("protocolo");
        });
        assertThat(columnsByIndex.values()).anySatisfy(columns -> assertThat(columns.getFirst()).isEqualTo("solicitante_id"));
        assertThat(columnsByIndex.values()).anySatisfy(columns -> assertThat(columns.getFirst()).isEqualTo("status"));
        assertThat(columnsByIndex.values()).anySatisfy(columns -> assertThat(columns.getFirst()).isEqualTo("categoria_id"));
    }
}

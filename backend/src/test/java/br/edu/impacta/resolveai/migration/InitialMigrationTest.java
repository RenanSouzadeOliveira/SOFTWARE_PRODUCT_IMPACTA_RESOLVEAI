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

        assertThat(migrationResult.migrationsExecuted).isEqualTo(6);
        assertThat(flyway.info().applied()).hasSize(6);

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            Set<String> tables = new HashSet<>();
            try (ResultSet result = metadata.getTables(null, "public", "%", new String[] {"TABLE"})) {
                while (result.next()) {
                    tables.add(result.getString("TABLE_NAME").toLowerCase());
                }
            }
            assertThat(tables).contains(
                    "usuarios", "categorias", "chamados", "comentarios", "historico_chamados", "historico_status");

            assertColumnSize(metadata, "chamados", "titulo", 120);
            assertColumnSize(metadata, "chamados", "descricao", 2000);
            try (ResultSet result = metadata.getColumns(null, "public", "chamados", "atribuido_em")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
            }
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

    @Test
    void shouldPreserveLegacyAssignmentsAndUseOnlyRecordedAttributionTime() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:assignment_upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3")).load().migrate();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO usuarios (id, nome, email, senha_hash, perfil) VALUES
                    (1, 'Solicitante ficticio', 'legado-requester@example.com', 'hash-ficticio', 'SOLICITANTE'),
                    (2, 'Atendente ficticio', 'legado-attendant@example.com', 'hash-ficticio', 'ATENDENTE')
                    """);
            statement.executeUpdate("INSERT INTO categorias (id, nome) VALUES (1, 'Legado')");
            statement.executeUpdate("""
                    INSERT INTO chamados (id, protocolo, titulo, descricao, status, solicitante_id, atendente_id, categoria_id)
                    VALUES (1, 'LEGADO-COM-EVIDENCIA', 'Chamado legado', 'Descricao suficientemente longa', 'EM_ATENDIMENTO', 1, 2, 1),
                    (2, 'LEGADO-SEM-EVIDENCIA', 'Chamado legado', 'Descricao suficientemente longa', 'EM_ATENDIMENTO', 1, 2, 1),
                    (3, 'LEGADO-ABERTO', 'Chamado legado', 'Descricao suficientemente longa', 'ABERTO', 1, NULL, 1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO historico_chamados (chamado_id, autor_id, tipo_evento, atendente_novo_id, criado_em)
                    VALUES (1, 2, 'ATRIBUICAO', 2, TIMESTAMP WITH TIME ZONE '2026-01-01 12:00:00+00')
                    """);
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        try (Connection connection = dataSource.getConnection();
                ResultSet result = connection.createStatement().executeQuery(
                        "SELECT id, status, atendente_id, atribuido_em FROM chamados ORDER BY id")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("status")).isEqualTo("EM_ATENDIMENTO");
            assertThat(result.getLong("atendente_id")).isEqualTo(2);
            assertThat(result.getTimestamp("atribuido_em").toInstant())
                    .isEqualTo(java.time.Instant.parse("2026-01-01T12:00:00Z"));
            assertThat(result.next()).isTrue();
            assertThat(result.getLong("atendente_id")).isEqualTo(2);
            assertThat(result.getTimestamp("atribuido_em")).isNull();
            assertThat(result.next()).isTrue();
            assertThat(result.getString("status")).isEqualTo("ABERTO");
            assertThat(result.getTimestamp("atribuido_em")).isNull();
        }
    }

    private void assertColumnSize(DatabaseMetaData metadata, String table, String column, int expectedSize)
            throws SQLException {
        try (ResultSet result = metadata.getColumns(null, "public", table, column)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt("COLUMN_SIZE")).isEqualTo(expectedSize);
        }
    }

    @Test
    void shouldBackfillOnlyEvidencedTransitionsAndAvoidDuplicateLegacyStatus() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:status_upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("4")).load().migrate();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO usuarios (id,nome,email,senha_hash,perfil) VALUES
                    (1,'Solicitante ficticio','status-requester@example.com','hash-ficticio','SOLICITANTE'),
                    (2,'Atendente ficticio','status-attendant@example.com','hash-ficticio','ATENDENTE')
                    """);
            statement.executeUpdate("INSERT INTO categorias (id,nome) VALUES (1,'Categoria')");
            statement.executeUpdate("""
                    INSERT INTO chamados (id,protocolo,titulo,descricao,solicitante_id,categoria_id)
                    VALUES (1,'RA-LEGADO','Chamado legado','Descricao suficientemente longa',1,1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO historico_chamados (chamado_id,autor_id,tipo_evento,status_anterior,status_novo,criado_em,justificativa)
                    VALUES
                    (1,2,'ATRIBUICAO','ABERTO','EM_ATENDIMENTO',TIMESTAMP WITH TIME ZONE '2026-01-01 12:00:00+00',NULL),
                    (1,2,'STATUS','ABERTO','EM_ATENDIMENTO',TIMESTAMP WITH TIME ZONE '2026-01-01 12:00:00+00',NULL),
                    (1,2,'STATUS','EM_ATENDIMENTO','RESOLVIDO',TIMESTAMP WITH TIME ZONE '2026-01-02 12:00:00+00','  Solucao legada  '),
                    (1,2,'ATRIBUICAO',NULL,NULL,TIMESTAMP WITH TIME ZONE '2026-01-03 12:00:00+00',NULL)
                    """);
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        try (Connection connection = dataSource.getConnection(); ResultSet result = connection.createStatement()
                .executeQuery("SELECT * FROM historico_status ORDER BY criado_em,id")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("status_anterior")).isEqualTo("ABERTO");
            assertThat(result.getLong("autor_id")).isEqualTo(2);
            assertThat(result.getString("observacao")).isNull();
            assertThat(result.next()).isTrue();
            assertThat(result.getString("status_novo")).isEqualTo("RESOLVIDO");
            assertThat(result.getString("observacao")).isEqualTo("Solucao legada");
            assertThat(result.next()).isFalse();
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

package br.edu.impacta.resolveai.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class CategoriaNormalizacaoMigrationTest {

    @Test
    void verificaAtualizacaoNoBancoDeTestes() throws SQLException {
        verificarAtualizacao(
                "jdbc:h2:mem:nome_categoria_legado;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "", false);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "P1_POSTGRES17_MIGRATION_URL", matches = "jdbc:postgresql:.+")
    void verificaAtualizacaoTransacionalNoPostgres17() throws SQLException {
        verificarAtualizacao(System.getenv("P1_POSTGRES17_MIGRATION_URL"),
                System.getenv().getOrDefault("P1_POSTGRES17_USER", "postgres"),
                System.getenv().getOrDefault("P1_POSTGRES17_PASSWORD", ""), true);
    }

    // A-03: a V11 independe da collation do banco. NFC x NFD passa pela V10 em qualquer locale.
    @Test
    @EnabledIfEnvironmentVariable(named = "P1_POSTGRES17_MIGRATION_URL", matches = "jdbc:postgresql:.+")
    void v11UnificaCaixaAcentosEspacosUnicodeNoPostgres17() throws SQLException {
        String url = System.getenv("P1_POSTGRES17_MIGRATION_URL");
        String usuario = System.getenv().getOrDefault("P1_POSTGRES17_USER", "postgres");
        String senha = System.getenv().getOrDefault("P1_POSTGRES17_PASSWORD", "");
        String schema = "categoria_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var config = Flyway.configure().dataSource(url, usuario, senha)
                .locations("classpath:db/migration").schemas(schema).defaultSchema(schema);
        config.target(MigrationVersion.fromVersion("10")).load().migrate();
        Flyway flyway = config.target(MigrationVersion.LATEST).load();

        try (var connection = DriverManager.getConnection(url, usuario, senha);
                var statement = connection.createStatement()) {
            try {
                connection.setSchema(schema);
                statement.executeUpdate("INSERT INTO categorias (id, nome, ativa) VALUES "
                        + "(1, 'Laborat\u00f3rio', TRUE), (2, 'Laborato\u0301rio', FALSE)");
                assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class)
                        .hasStackTraceContaining("nomes Unicode equivalentes");
                try (var total = statement.executeQuery("SELECT COUNT(*) FROM categorias")) {
                    assertThat(total.next()).isTrue();
                    assertThat(total.getInt(1)).isEqualTo(2);
                }

                statement.executeUpdate("UPDATE categorias SET nome = 'Laborat\u00f3rio antigo' WHERE id = 2");
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
                flyway.validate();
                for (String equivalente : new String[] {"LABORAT\u00d3RIO", "laborato\u0301rio",
                        "\u00a0Laborat\u00f3rio\u3000", "LABORAT\u00d3RIO ANTIGO"}) {
                    assertThatThrownBy(() -> statement.executeUpdate(
                            "INSERT INTO categorias (id, nome, ativa) VALUES (9, '" + equivalente + "', TRUE)"))
                            .isInstanceOfSatisfying(SQLException.class, error ->
                                    assertThat(error.getSQLState()).isEqualTo("23505"));
                }
                assertThat(statement.executeUpdate(
                        "INSERT INTO categorias (id, nome, ativa) VALUES (9, 'Laboratorio', TRUE)")).isEqualTo(1);
            } finally {
                connection.setSchema("public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private void verificarAtualizacao(String url, String usuario, String senha, boolean postgres) throws SQLException {
        String schema = "categoria_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var config = Flyway.configure().dataSource(url, usuario, senha)
                .locations("classpath:db/migration").schemas(schema).defaultSchema(schema);
        config.target(MigrationVersion.fromVersion("9")).load().migrate();
        Flyway flyway = config.target(MigrationVersion.fromVersion("10")).load();

        try (var connection = DriverManager.getConnection(url, usuario, senha);
                var statement = connection.createStatement()) {
            try {
                connection.setSchema(schema);
                if (postgres) {
                    try (var versao = statement.executeQuery("SHOW server_version_num")) {
                        assertThat(versao.next()).isTrue();
                        assertThat(versao.getInt(1)).isBetween(170000, 179999);
                    }
                }
                statement.executeUpdate("""
                        INSERT INTO categorias (id, nome, ativa)
                        VALUES (1, 'Laboratorio', TRUE), (2, '  LABORATORIO  ', FALSE)
                        """);
                assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class)
                        .hasStackTraceContaining("Existem categorias com nomes equivalentes.");
                try (var categorias = statement.executeQuery("SELECT nome, ativa FROM categorias ORDER BY id")) {
                    assertThat(categorias.next()).isTrue();
                    assertThat(categorias.getString("nome")).isEqualTo("Laboratorio");
                    assertThat(categorias.getBoolean("ativa")).isTrue();
                    assertThat(categorias.next()).isTrue();
                    assertThat(categorias.getString("nome")).isEqualTo("  LABORATORIO  ");
                    assertThat(categorias.getBoolean("ativa")).isFalse();
                    assertThat(categorias.next()).isFalse();
                }

                if (!postgres) {
                    flyway.repair();
                }
                statement.executeUpdate("UPDATE categorias SET nome = 'Laboratorio legado' WHERE id = 2");
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
                flyway.validate();
                assertThatThrownBy(() -> statement.executeUpdate("""
                        INSERT INTO categorias (id, nome, ativa) VALUES (3, 'laboratorio legado ', TRUE)
                        """)).isInstanceOfSatisfying(SQLException.class, error ->
                        assertThat(error.getSQLState()).isEqualTo("23505"));
            } finally {
                connection.setSchema("public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}

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

    private void verificarAtualizacao(String url, String usuario, String senha, boolean postgres) throws SQLException {
        String schema = "categoria_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var config = Flyway.configure().dataSource(url, usuario, senha)
                .locations("classpath:db/migration").schemas(schema).defaultSchema(schema);
        config.target(MigrationVersion.fromVersion("9")).load().migrate();
        Flyway flyway = config.target(MigrationVersion.LATEST).load();

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
                        VALUES (1, 'Laboratório', TRUE), (2, '  LABORATÓRIO  ', FALSE)
                        """);
                assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class)
                        .hasStackTraceContaining("Existem categorias com nomes equivalentes.");
                try (var categorias = statement.executeQuery("SELECT nome, ativa FROM categorias ORDER BY id")) {
                    assertThat(categorias.next()).isTrue();
                    assertThat(categorias.getString("nome")).isEqualTo("Laboratório");
                    assertThat(categorias.getBoolean("ativa")).isTrue();
                    assertThat(categorias.next()).isTrue();
                    assertThat(categorias.getString("nome")).isEqualTo("  LABORATÓRIO  ");
                    assertThat(categorias.getBoolean("ativa")).isFalse();
                    assertThat(categorias.next()).isFalse();
                }

                if (!postgres) {
                    flyway.repair();
                }
                statement.executeUpdate("UPDATE categorias SET nome = 'Laboratório legado' WHERE id = 2");
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
                flyway.validate();
                assertThatThrownBy(() -> statement.executeUpdate("""
                        INSERT INTO categorias (id, nome, ativa) VALUES (3, 'laboratório legado ', TRUE)
                        """)).isInstanceOfSatisfying(SQLException.class, error ->
                        assertThat(error.getSQLState()).isEqualTo("23505"));
            } finally {
                connection.setSchema("public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}

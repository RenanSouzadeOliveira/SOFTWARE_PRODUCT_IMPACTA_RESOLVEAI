package br.edu.impacta.resolveai.migration;

import static org.assertj.core.api.Assertions.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class ComentarioMigrationTest {
    @Test void shouldNormalizeValidLegacyContentAndReplaceTheIndexWithStableOrder() throws SQLException {
        JdbcDataSource dataSource = legado("comments_valid_upgrade", "  Mensagem legada  ");
        migrate(dataSource);
        try (Connection connection = dataSource.getConnection(); ResultSet result = connection.createStatement()
                .executeQuery("SELECT conteudo,autor_id FROM comentarios")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("conteudo")).isEqualTo("Mensagem legada");
            assertThat(result.getLong("autor_id")).isEqualTo(1);
        }
        try (Connection connection = dataSource.getConnection(); ResultSet result = connection.getMetaData()
                .getIndexInfo(null, "public", "comentarios", false, false)) {
            java.util.List<String> columns = new java.util.ArrayList<>();
            while (result.next()) {
                if ("idx_comentarios_chamado_data_id".equals(result.getString("INDEX_NAME")))
                    columns.add(result.getString("COLUMN_NAME"));
                assertThat(result.getString("INDEX_NAME")).isNotEqualTo("idx_comentarios_chamado_criado_em");
            }
            assertThat(columns).containsExactly("chamado_id", "criado_em", "id");
        }
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO comentarios (chamado_id,autor_id,conteudo) VALUES (1,1,?)")) {
            for (String invalid : new String[] {"   ", "x".repeat(2001), " espacos externos "}) {
                statement.setString(1, invalid);
                assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class);
            }
            statement.setString(1, "x".repeat(2000));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    @Test void shouldFailExplicitlyForInvalidLegacyWithoutTruncationOrDeletion() throws SQLException {
        String original = "x".repeat(2001);
        JdbcDataSource dataSource = legado("comments_invalid_upgrade", original);
        assertThatThrownBy(() -> migrate(dataSource)).isInstanceOf(FlywayException.class);
        try (Connection connection = dataSource.getConnection(); ResultSet result = connection.createStatement()
                .executeQuery("SELECT conteudo FROM comentarios")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("conteudo")).isEqualTo(original);
            assertThat(result.next()).isFalse();
        }
    }

    private void migrate(JdbcDataSource dataSource) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    private JdbcDataSource legado(String name, String content) throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("5")).load().migrate();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO usuarios (id,nome,email,senha_hash,perfil)
                    VALUES (1,'Pessoa ficticia','legacy-comment@example.com','hash-ficticio','SOLICITANTE')
                    """);
            statement.executeUpdate("INSERT INTO categorias (id,nome) VALUES (1,'Categoria')");
            statement.executeUpdate("""
                    INSERT INTO chamados (id,protocolo,titulo,descricao,solicitante_id,categoria_id)
                    VALUES (1,'RA-LEGADO','Chamado legado','Descricao suficientemente longa',1,1)
                    """);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO comentarios (chamado_id,autor_id,conteudo) VALUES (1,1,?)")) {
                insert.setString(1, content);
                insert.executeUpdate();
            }
        }
        return dataSource;
    }
}

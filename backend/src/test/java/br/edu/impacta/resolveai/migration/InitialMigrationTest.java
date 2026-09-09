package br.edu.impacta.resolveai.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import org.flywaydb.core.api.output.MigrateResult;
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

        assertThat(migrationResult.migrationsExecuted).isOne();
        assertThat(flyway.info().applied()).hasSize(1);

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
        }
    }
}

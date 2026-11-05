package br.edu.impacta.resolveai.migration;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class PaginacaoMigrationTest {
    @Test void shouldUpgradeV6PreservingTicketsAndReplacingOnlyRedundantIndexes() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:pagination_upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("6").load().migrate();
        try (Connection c = ds.getConnection(); var s = c.createStatement()) {
            s.execute("INSERT INTO usuarios (id,nome,email,senha_hash,perfil) VALUES (1,'Pessoa AC3','ac3@example.invalid','ficticio','SOLICITANTE')");
            s.execute("INSERT INTO categorias (id,nome) VALUES (1,'Categoria AC3')");
            s.execute("INSERT INTO chamados (protocolo,titulo,descricao,status,prioridade,solicitante_id,categoria_id) VALUES ('AC3-001','Título fictício','Descrição fictícia suficiente','ABERTO','MEDIA',1,1)");
        }
        var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        try (Connection c = ds.getConnection(); var s = c.createStatement()) {
            try (var result = s.executeQuery("SELECT COUNT(*) FROM chamados")) {
                result.next(); assertThat(result.getInt(1)).isEqualTo(1);
            }
            List<String> indexes = new ArrayList<>();
            try (var result = c.getMetaData().getIndexInfo(null, "public", "chamados", false, false)) {
                while (result.next()) if (result.getString("INDEX_NAME") != null) indexes.add(result.getString("INDEX_NAME").toLowerCase());
            }
            assertThat(indexes).contains("idx_chamados_solicitante_criado_id", "idx_chamados_fila_ordem", "idx_chamados_carteira_criado_id")
                    .doesNotContain("idx_chamados_solicitante_criado_em", "idx_chamados_fila_criado_id");
        }
    }
}

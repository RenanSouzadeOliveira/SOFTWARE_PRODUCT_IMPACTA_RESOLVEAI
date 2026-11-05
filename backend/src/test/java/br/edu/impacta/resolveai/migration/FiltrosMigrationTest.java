package br.edu.impacta.resolveai.migration;

import static org.assertj.core.api.Assertions.assertThat;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class FiltrosMigrationTest {
    @Test void upgradesV7PreservingDataAndExistingIndexes() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:filters_upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"); ds.setUser("sa");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("7").load().migrate();
        try (var c=ds.getConnection(); var s=c.createStatement()) {
            s.execute("INSERT INTO usuarios (id,nome,email,senha_hash,perfil) VALUES (1,'Pessoa fictícia','filtros@example.invalid','ficticio','SOLICITANTE')");
            s.execute("INSERT INTO categorias (id,nome) VALUES (1,'Filtros fictícios')");
            s.execute("INSERT INTO chamados (protocolo,titulo,descricao,solicitante_id,categoria_id) VALUES ('FILTROS-001','Título fictício','Descrição fictícia suficiente',1,1)");
        }
        var flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1); flyway.validate();
        try(var c=ds.getConnection();var s=c.createStatement();var rs=s.executeQuery("SELECT COUNT(*) FROM chamados WHERE protocolo='FILTROS-001'")) {
            rs.next();assertThat(rs.getInt(1)).isEqualTo(1);
            var names = new java.util.ArrayList<String>();
            try(var indexes=c.getMetaData().getIndexInfo(null,"public","chamados",false,false)) {
                while(indexes.next()) if(indexes.getString("INDEX_NAME")!=null) names.add(indexes.getString("INDEX_NAME").toLowerCase());
            }
            assertThat(names).contains("idx_chamados_criado_id","idx_chamados_solicitante_criado_id","idx_chamados_fila_ordem","idx_chamados_carteira_criado_id");
        }
    }
}

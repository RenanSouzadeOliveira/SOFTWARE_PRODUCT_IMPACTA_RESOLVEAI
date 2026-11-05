package br.edu.impacta.resolveai.migration;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

class HistoricoMigrationTest {
    @Test void upgradesV8PreservingAllEvidenceAndProtectsPostgresHistory() throws Exception {
        String url=System.getenv("HISTORY_MIGRATION_URL");
        DataSource ds;
        if(url==null) {
            var h2=new JdbcDataSource();
            h2.setURL("jdbc:h2:mem:history_upgrade;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");h2.setUser("sa");ds=h2;
        } else ds=new DriverManagerDataSource(url,"history_test","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("8").load().migrate();
        try(var c=ds.getConnection();var s=c.createStatement()) {
            s.execute("INSERT INTO usuarios (id,nome,email,senha_hash,perfil) VALUES (1,'Pessoa fictícia','history@example.invalid','ficticio','SOLICITANTE')");
            s.execute("INSERT INTO categorias (id,nome) VALUES (1,'Histórico acadêmico')");
            s.execute("INSERT INTO chamados (id,protocolo,titulo,descricao,solicitante_id,categoria_id) VALUES (1,'HIST-001','Título fictício','Descrição fictícia suficiente',1,1)");
            s.execute("INSERT INTO historico_chamados (chamado_id,autor_id,tipo_evento,prioridade_anterior,prioridade_nova,criado_em) VALUES (1,1,'PRIORIDADE','MEDIA','ALTA',TIMESTAMP WITH TIME ZONE '2026-01-01 10:00:00+00')");
            s.execute("INSERT INTO historico_status (chamado_id,autor_id,status_anterior,status_novo,observacao,criado_em) VALUES (1,1,'ABERTO','EM_ATENDIMENTO','Observação legada',TIMESTAMP WITH TIME ZONE '2026-01-01 10:00:00+00')");
        }
        String general=rows(ds,"historico_chamados"), statuses=rows(ds,"historico_status");
        var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);flyway.validate();
        assertThat(rows(ds,"historico_chamados")).isEqualTo(general);
        assertThat(rows(ds,"historico_status")).isEqualTo(statuses);
        try(var c=ds.getConnection();var s=c.createStatement()) {
            assertThatThrownBy(()->s.execute("INSERT INTO historico_chamados (chamado_id,autor_id,tipo_evento) VALUES (999999,1,'CRIACAO')")).isInstanceOf(SQLException.class);
            if(c.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
                for(String table:new String[]{"historico_chamados","historico_status"}) {
                    for(String sql:new String[]{"UPDATE "+table+" SET autor_id=1", "DELETE FROM "+table, "TRUNCATE "+table})
                        assertThatThrownBy(()->s.execute(sql)).isInstanceOf(SQLException.class).hasMessageContaining("imutavel");
                }
                s.execute("UPDATE usuarios SET ativo=false WHERE id=1");
                assertThatThrownBy(()->s.execute("DELETE FROM usuarios WHERE id=1")).isInstanceOf(SQLException.class);
            }
            try(var rs=c.getMetaData().getIndexInfo(null,"public","historico_chamados",false,false)) {
                var cols=new java.util.ArrayList<String>();
                while(rs.next()) if("idx_historico_chamado_data_id".equalsIgnoreCase(rs.getString("INDEX_NAME"))) cols.add(rs.getString("COLUMN_NAME"));
                assertThat(cols).containsExactly("chamado_id","criado_em","id");
            }
        }
    }
    private String rows(DataSource ds,String table) throws Exception {
        StringBuilder out=new StringBuilder();
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT * FROM "+table+" ORDER BY id")) {
            while(r.next()) for(int i=1;i<=r.getMetaData().getColumnCount();i++) out.append(r.getString(i)).append('|');
        }
        return out.toString();
    }
}

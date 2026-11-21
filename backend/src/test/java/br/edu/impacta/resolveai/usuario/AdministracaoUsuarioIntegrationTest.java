package br.edu.impacta.resolveai.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import br.edu.impacta.resolveai.atendimento.AtendimentoService;
import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.ChamadoRepository;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.usuario.dto.AlterarSituacaoUsuarioRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import br.edu.impacta.resolveai.security.JwtService;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.usuario.dto.AtualizarUsuarioRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdministracaoUsuarioIntegrationTest {
    private static final String SENHA = "senha-ficticia-123";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired AdministracaoUsuarioService administracao;
    @Autowired AtendimentoService atendimento;
    @Autowired ChamadoRepository chamados;
    @Autowired CategoriaRepository categorias;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    void criaAtualizaListaEDesativaSemExporSenha() throws Exception {
        Usuario admin = criar("Administrador", "admin-contas@example.com", Perfil.ADMIN);
        String token = token(admin);
        var criado = mvc.perform(post("/api/admin/usuarios").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "nome", "Pessoa", "email", "  PESSOA@EXAMPLE.COM  ",
                        "perfil", "ATENDENTE", "senha", SENHA))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("pessoa@example.com"))
                .andExpect(jsonPath("$.perfil").value("ATENDENTE"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist()).andReturn();
        long id = json.readTree(criado.getResponse().getContentAsString()).get("id").asLong();
        Usuario pessoa = usuarios.findById(id).orElseThrow();
        assertThat(encoder.matches(SENHA, pessoa.senhaHashParaAutenticacao())).isTrue();
        String tokenPessoa = token(pessoa);
        mvc.perform(patch("/api/admin/usuarios/{id}", id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "nome", "Pessoa Atualizada", "email", " NOVO@EXAMPLE.COM ", "perfil", "SOLICITANTE"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("novo@example.com"))
                .andExpect(jsonPath("$.perfil").value("SOLICITANTE"));
        mvc.perform(get("/api/usuarios/me").header("Authorization", tokenPessoa))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("SOLICITANTE"));
        mvc.perform(patch("/api/admin/usuarios/{id}/situacao", id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(get("/api/usuarios/me").header("Authorization", tokenPessoa)).andExpect(status().isUnauthorized());
        var listagem = mvc.perform(get("/api/admin/usuarios").header("Authorization", token))
                .andExpect(status().isOk()).andReturn();
        var lista = json.readTree(listagem.getResponse().getContentAsString());
        assertThat(lista.findValuesAsText("id")).contains(String.valueOf(id));
        for (var item : lista) {
            if (item.get("id").asLong() == id) assertThat(item.get("ativo").asBoolean()).isFalse();
        }
        mvc.perform(patch("/api/admin/usuarios/{id}/situacao", id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
        mvc.perform(get("/api/usuarios/me").header("Authorization", tokenPessoa)).andExpect(status().isOk());
    }

    @Test
    void impedeAutodesativacaoEProprioRebaixamento() throws Exception {
        Usuario admin = criar("Administrador", "autoprotecao@example.com", Perfil.ADMIN);
        mvc.perform(patch("/api/admin/usuarios/{id}/situacao", admin.id()).header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/usuarios/{id}", admin.id()).header("Authorization", token(admin))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "nome", "Administrador", "email", admin.email(), "perfil", "ATENDENTE"))))
                .andExpect(status().isForbidden());
        assertThat(admin.ativo()).isTrue();
        assertThat(admin.perfil()).isEqualTo(Perfil.ADMIN);
    }

    @Test
    void protegeRotasParaTodosPerfisSemPermissao() throws Exception {
        mvc.perform(get("/api/admin/usuarios")).andExpect(status().isUnauthorized());
        for (Perfil perfil : new Perfil[]{Perfil.SOLICITANTE, Perfil.ATENDENTE}) {
            Usuario usuario = criar("Pessoa", perfil.name() + "@example.com", perfil);
            String token = token(usuario);
            mvc.perform(get("/api/admin/usuarios").header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/usuarios").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/admin/usuarios/{id}", usuario.id()).header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(patch("/api/admin/usuarios/{id}/situacao", usuario.id()).header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void validaDadosPerfilSenhaMultibyteEIdentificadores() throws Exception {
        Usuario admin = criar("Administrador", "validacoes@example.com", Perfil.ADMIN);
        String token = token(admin);
        for (String corpo : new String[]{"{}", "{\"nome\":\"Pessoa\",\"email\":\"invalido\",\"perfil\":\"ADMIN\",\"senha\":\"senha-ficticia\"}",
                "{\"nome\":\"Pessoa\",\"email\":\"pessoa@example.com\",\"perfil\":\"DONO\",\"senha\":\"senha-ficticia\"}"}) {
            mvc.perform(post("/api/admin/usuarios").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/usuarios").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "nome", "Pessoa", "email", "multibyte@example.com", "perfil", "SOLICITANTE", "senha", "á".repeat(40)))))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/usuarios/0/situacao").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/usuarios/999999/situacao").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":true}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/usuarios/{id}/situacao", admin.id()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaCoercaoDeIndicesNumericosParaPerfil() throws Exception {
        Usuario admin = criar("Administrador", "perfil-estrito@example.com", Perfil.ADMIN);
        String token = token(admin);
        for (Object perfil : new Object[]{0, 1, 2, "0", true}) {
            String corpo = json.writeValueAsString(Map.of("nome", "Pessoa", "email", "pessoa@example.com",
                    "perfil", perfil, "senha", SENHA));
            mvc.perform(post("/api/admin/usuarios").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest());
            mvc.perform(patch("/api/admin/usuarios/{id}", admin.id()).header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void recusaEmailDuplicadoNormalizadoNaCriacaoENaEdicao() throws Exception {
        Usuario admin = criar("Administrador", "duplicado@example.com", Perfil.ADMIN);
        Usuario outro = criar("Pessoa", "outra@example.com", Perfil.ATENDENTE);
        String token = token(admin);
        mvc.perform(post("/api/admin/usuarios").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "nome", "Pessoa", "email", " DUPLICADO@EXAMPLE.COM ", "perfil", "ATENDENTE", "senha", SENHA))))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/usuarios/{id}", outro.id()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "nome", "Pessoa", "email", " DUPLICADO@EXAMPLE.COM ", "perfil", "SOLICITANTE"))))
                .andExpect(status().isConflict());
        assertThat(outro.email()).isEqualTo("outra@example.com");
    }

    @Test
    void revalidaPerfilDoAtorMesmoComPrincipalAntigo() {
        Usuario ator = criar("Pessoa", "ator-antigo@example.com", Perfil.ATENDENTE);
        Usuario alvo = criar("Pessoa", "alvo@example.com", Perfil.SOLICITANTE);
        var principalAntigo = new UsuarioPrincipal(ator.id(), ator.email(), Perfil.ADMIN);
        assertThatThrownBy(() -> administracao.atualizar(alvo.id(), principalAntigo,
                new AtualizarUsuarioRequest("Alterado", alvo.email(), Perfil.ADMIN)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(alvo.perfil()).isEqualTo(Perfil.SOLICITANTE);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void desativacaoEsperaAutoatribuicaoEmCursoEPreservaHistorico() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String sufixo = UUID.randomUUID().toString();
        Long[] ids = tx.execute(estado -> {
            Usuario admin = criar("ZZ Administrador", "admin-" + sufixo + "@example.com", Perfil.ADMIN);
            Usuario atendente = criar("ZZ Atendente", "atendente-" + sufixo + "@example.com", Perfil.ATENDENTE);
            Usuario solicitante = criar("ZZ Solicitante", "solicitante-" + sufixo + "@example.com", Perfil.SOLICITANTE);
            Categoria categoria = categorias.save(Categoria.nova("Concorrência " + sufixo, null, Instant.now()));
            Chamado chamado = chamados.saveAndFlush(Chamado.abrir("CONC-" + sufixo.substring(0, 20),
                    "Chamado concorrente", "Descrição fictícia para validar concorrência", solicitante, categoria, Instant.now()));
            return new Long[]{admin.id(), atendente.id(), chamado.id()};
        });
        var atribuicaoPendente = new CountDownLatch(1);
        var liberarCommit = new CountDownLatch(1);
        var administracaoIniciada = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var atribuicao = executor.submit(() -> tx.execute(estado -> {
                atendimento.assumir(ids[2], new UsuarioPrincipal(ids[1], "ignorado@example.com", Perfil.ATENDENTE));
                atribuicaoPendente.countDown();
                aguardar(liberarCommit);
                return true;
            }));
            assertThat(atribuicaoPendente.await(10, TimeUnit.SECONDS)).isTrue();
            var desativacao = executor.submit(() -> {
                administracaoIniciada.countDown();
                return administracao.alterarSituacao(ids[1],
                        new UsuarioPrincipal(ids[0], "ignorado@example.com", Perfil.ADMIN),
                        new AlterarSituacaoUsuarioRequest(false));
            });
            assertThat(administracaoIniciada.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> desativacao.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            liberarCommit.countDown();
            assertThat(atribuicao.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(desativacao.get(10, TimeUnit.SECONDS).ativo()).isFalse();
            tx.executeWithoutResult(estado -> {
                assertThat(usuarios.findById(ids[1]).orElseThrow().ativo()).isFalse();
                Chamado chamado = chamados.findById(ids[2]).orElseThrow();
                assertThat(chamado.atendente().id()).isEqualTo(ids[1]);
                assertThat(chamado.status()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
                assertThat(jdbc.queryForObject("select count(*) from historico_chamados where chamado_id = ? and tipo_evento = 'ATRIBUICAO'",
                        Long.class, ids[2])).isEqualTo(1L);
                assertThat(jdbc.queryForObject("select count(*) from historico_status where chamado_id = ?",
                        Long.class, ids[2])).isEqualTo(1L);
            });
        } finally {
            liberarCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void aguardar(CountDownLatch sinal) {
        try {
            if (!sinal.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Tempo de sincronização excedido");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sincronização interrompida", exception);
        }
    }

    private Usuario criar(String nome, String email, Perfil perfil) {
        return usuarios.saveAndFlush(Usuario.novo(nome, email, encoder.encode(SENHA), perfil, Instant.now()));
    }

    private String token(Usuario usuario) { return "Bearer " + jwt.generate(usuario); }
}

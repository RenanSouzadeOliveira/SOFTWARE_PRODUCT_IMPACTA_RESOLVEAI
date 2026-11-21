package br.edu.impacta.resolveai.categoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import br.edu.impacta.resolveai.atendimento.AtendimentoService;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoriaAdminIntegrationTest {

    private static final String CATEGORIAS = "/api/admin/categorias";
    private static final String USUARIOS = "/api/admin/usuarios";
    private static final String SENHA = "Senha-fictícia-170!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AtendimentoService atendimento;
    @Autowired JdbcTemplate jdbc;

    private Usuario admin;
    private String tokenAdmin;

    @BeforeEach
    void autenticarAdmin() throws Exception {
        admin = conta(Perfil.ADMIN);
        tokenAdmin = token(admin.email());
    }

    @Test
    void validaPermissoesCamposEReservaNomeMesmoInativo() throws Exception {
        String tokenSolicitante = token(conta(Perfil.SOLICITANTE).email());
        String tokenAtendente = token(conta(Perfil.ATENDENTE).email());
        mvc.perform(get(CATEGORIAS)).andExpect(status().isUnauthorized());
        for (String token : new String[] {tokenSolicitante, tokenAtendente}) {
            consultar(CATEGORIAS, token, 403);
            enviar(post(CATEGORIAS), token, Map.of("nome", "Categoria"), 403);
            enviar(patch(CATEGORIAS + "/1"), token, Map.of("nome", "Categoria"), 403);
            enviar(patch(CATEGORIAS + "/1/situacao"), token, Map.of("ativa", false), 403);
        }

        String nome = "Acesso acadêmico " + UUID.randomUUID();
        enviar(post(CATEGORIAS), tokenAdmin, Map.of("nome", " \t "), 400);
        enviar(post(CATEGORIAS), tokenAdmin, Map.of("nome", "x".repeat(101)), 400);
        enviar(post(CATEGORIAS), tokenAdmin, Map.of("nome", nome, "descricao", "x".repeat(256)), 400);

        JsonNode criada = enviar(post(CATEGORIAS), tokenAdmin, Map.of("nome", " " + nome + " ",
                "descricao", "  Dúvida de acesso  "), 201);
        long id = criada.path("id").asLong();
        assertThat(criada.path("nome").asText()).isEqualTo(nome);
        assertThat(criada.path("descricao").asText()).isEqualTo("Dúvida de acesso");
        assertThat(criada.path("ativa").asBoolean()).isTrue();

        String categoria = CATEGORIAS + "/" + id;
        enviar(patch(categoria + "/situacao"), tokenAdmin, Map.of(), 400);
        enviar(patch(CATEGORIAS + "/" + Long.MAX_VALUE), tokenAdmin, Map.of("nome", nome), 404);
        JsonNode desativada = enviar(patch(categoria + "/situacao"), tokenAdmin, Map.of("ativa", false), 200);
        assertThat(desativada.path("ativa").asBoolean()).isFalse();

        JsonNode conflito = enviar(post(CATEGORIAS), tokenAdmin,
                Map.of("nome", " \t" + nome.toUpperCase(java.util.Locale.ROOT) + " "), 409);
        assertThat(conflito.path("message").asText()).isEqualTo("Já existe uma categoria com esse nome");
        String outra = "Outra categoria " + UUID.randomUUID();
        long outroId = enviar(post(CATEGORIAS), tokenAdmin, Map.of("nome", outra), 201).path("id").asLong();
        enviar(patch(CATEGORIAS + "/" + outroId), tokenAdmin, Map.of("nome", nome), 409);
        assertThat(jdbc.queryForObject("SELECT nome FROM categorias WHERE id = ?", String.class, outroId))
                .isEqualTo(outra);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO categorias (nome) VALUES (?)", "\t" + nome + " "))
                .isInstanceOf(DataIntegrityViolationException.class);
        mvc.perform(delete(categoria).header("Authorization", tokenAdmin)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void bancoArbitraDoisCadastrosSimultaneosDoMesmoNome() throws Exception {
        String nome = "Infraestrutura " + UUID.randomUUID();
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var primeiro = executor.submit(() -> criarConcorrente(inicio, nome));
            var segundo = executor.submit(() -> criarConcorrente(inicio, nome.toUpperCase(java.util.Locale.ROOT)));
            inicio.countDown();
            assertThat(new int[] {primeiro.get(15, TimeUnit.SECONDS), segundo.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    @Test
    void preservaChamadoHistoricoExportacaoEDashboardAoDesativarEReativar() throws Exception {
        String emailAgente = emailFicticio();
        long agenteId = enviar(post(USUARIOS), tokenAdmin, Map.of(
                "nome", "Atendente fictício", "email", emailAgente, "senha", SENHA, "perfil", "ATENDENTE"),
                201).path("id").asLong();
        String tokenAgente = token(emailAgente);
        String tokenSolicitante = token(conta(Perfil.SOLICITANTE).email());
        long categoriaId = enviar(post(CATEGORIAS), tokenAdmin,
                Map.of("nome", "Laboratório " + UUID.randomUUID()), 201).path("id").asLong();
        String nomeEditado = "Serviço acadêmico " + UUID.randomUUID();
        String categoria = CATEGORIAS + "/" + categoriaId;
        JsonNode editada = enviar(patch(categoria), tokenAdmin,
                Map.of("nome", nomeEditado, "descricao", "Apoio aos laboratórios"), 200);
        assertThat(editada.path("nome").asText()).isEqualTo(nomeEditado);

        mvc.perform(get("/api/categorias").header("Authorization", tokenSolicitante))
                .andExpect(jsonPath("$[*].id", hasItem((int) categoriaId)));
        long chamadoId = abrir(tokenSolicitante, categoriaId, 201).path("id").asLong();
        long aguardandoId = abrir(tokenSolicitante, categoriaId, 201).path("id").asLong();
        String chamado = "/api/chamados/" + chamadoId;
        String assumirPendente = "/api/chamados/" + aguardandoId + "/assumir";
        mvc.perform(patch(chamado + "/assumir").header("Authorization", tokenAgente)).andExpect(status().isOk());

        JsonNode detalhes = consultar(chamado, tokenSolicitante, 200);
        assertThat(detalhes.at("/atendente/id").asLong()).isEqualTo(agenteId);
        assertThat(detalhes.at("/categoria/id").asLong()).isEqualTo(categoriaId);
        JsonNode historico = consultar(chamado + "/historico", tokenAdmin, 200);
        assertThat(historico.findValuesAsText("tipoEvento")).contains("CRIACAO", "ATRIBUICAO");
        assertThat(historico.findValuesAsText("codigo")).contains(Long.toString(agenteId));
        JsonNode painel = consultar("/api/dashboard/resumo", tokenSolicitante, 200);
        assertThat(painel.path("total").asLong()).isEqualTo(2);
        assertThat(painel.path("emAtendimento").asLong()).isEqualTo(1);
        assertThat(painel.path("porCategoria").findValuesAsText("nome")).contains(nomeEditado);

        String situacaoAgente = USUARIOS + "/" + agenteId + "/situacao";
        enviar(patch(situacaoAgente), tokenAdmin, Map.of("ativo", false), 200);
        enviar(patch(categoria + "/situacao"), tokenAdmin, Map.of("ativa", false), 200);
        consultar(chamado, tokenAgente, 401);
        enviar(post("/api/auth/login"), null, Map.of("email", emailAgente, "senha", SENHA), 401);
        mvc.perform(patch(assumirPendente).header("Authorization", tokenAgente)).andExpect(status().isUnauthorized());
        var agenteInativo = new UsuarioPrincipal(agenteId, emailAgente, Perfil.ATENDENTE);
        assertThatThrownBy(() -> atendimento.assumir(aguardandoId, agenteInativo))
                .isInstanceOf(NaoAutorizadoException.class);

        assertThat(consultar(chamado, tokenSolicitante, 200)).isEqualTo(detalhes);
        assertThat(consultar(chamado + "/historico", tokenAdmin, 200)).isEqualTo(historico);
        assertThat(consultar("/api/dashboard/resumo", tokenSolicitante, 200)).isEqualTo(painel);
        assertThat(consultar("/api/dashboard/resumo", tokenAdmin, 200).path("porCategoria")
                .findValuesAsText("nome")).contains(nomeEditado);
        mvc.perform(get("/api/chamados/exportacao").param("categoriaId", Long.toString(categoriaId))
                        .header("Authorization", tokenSolicitante))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(nomeEditado)));
        mvc.perform(get("/api/categorias").header("Authorization", tokenSolicitante))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) categoriaId))));
        mvc.perform(get(CATEGORIAS).header("Authorization", tokenAdmin))
                .andExpect(jsonPath("$[?(@.id == " + categoriaId + ")].ativa", hasItem(false)));
        abrir(tokenSolicitante, categoriaId, 400);

        assertThat(jdbc.queryForObject("SELECT atendente_id FROM chamados WHERE id = ?", Long.class, chamadoId))
                .isEqualTo(agenteId);
        assertThat(jdbc.queryForObject("SELECT categoria_id FROM chamados WHERE id = ?", Long.class, chamadoId))
                .isEqualTo(categoriaId);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM categorias WHERE id = ?", categoriaId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM usuarios WHERE id = ?", agenteId))
                .isInstanceOf(DataIntegrityViolationException.class);

        consultar(USUARIOS, tokenSolicitante, 403);
        consultar(CATEGORIAS, tokenSolicitante, 403);
        enviar(patch(USUARIOS + "/" + admin.id() + "/situacao"), tokenAdmin, Map.of("ativo", false), 403);
        enviar(patch(USUARIOS + "/" + admin.id()), tokenAdmin,
                Map.of("nome", admin.nome(), "email", admin.email(), "perfil", "ATENDENTE"), 403);

        enviar(patch(situacaoAgente), tokenAdmin, Map.of("ativo", true), 200);
        enviar(patch(categoria + "/situacao"), tokenAdmin, Map.of("ativa", true), 200);
        mvc.perform(patch(assumirPendente).header("Authorization", token(emailAgente))).andExpect(status().isOk());
        mvc.perform(get("/api/categorias").header("Authorization", tokenSolicitante))
                .andExpect(jsonPath("$[*].id", hasItem((int) categoriaId)));
        abrir(tokenSolicitante, categoriaId, 201);
        assertThat(consultar(chamado + "/historico", tokenAdmin, 200)).isEqualTo(historico);
    }

    private int criarConcorrente(CountDownLatch inicio, String nome) throws Exception {
        if (!inicio.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("O teste não liberou a criação de categoria");
        }
        return mvc.perform(post(CATEGORIAS).header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("nome", nome))))
                .andReturn().getResponse().getStatus();
    }

    private JsonNode abrir(String token, long categoria, int statusEsperado) throws Exception {
        return enviar(post("/api/chamados"), token, Map.of("titulo", "Solicitação de laboratório",
                "descricao", "Equipamento de teste não está disponível.", "prioridade", "MEDIA",
                "categoriaId", categoria), statusEsperado);
    }

    private Usuario conta(Perfil perfil) {
        return usuarios.save(Usuario.novo("Conta fictícia", emailFicticio(), passwordEncoder.encode(SENHA),
                perfil, Instant.now()));
    }

    private String emailFicticio() {
        return UUID.randomUUID() + "@example.invalid";
    }

    private String token(String email) throws Exception {
        return "Bearer " + enviar(post("/api/auth/login"), null, Map.of("email", email, "senha", SENHA),
                200).path("accessToken").asText();
    }

    private JsonNode consultar(String url, String token, int statusEsperado) throws Exception {
        var resposta = mvc.perform(get(url).header("Authorization", token))
                .andExpect(status().is(statusEsperado)).andReturn().getResponse();
        return json.readTree(resposta.getContentAsByteArray());
    }

    private JsonNode enviar(MockHttpServletRequestBuilder requisicao, String token, Object corpo, int statusEsperado)
            throws Exception {
        if (token != null) {
            requisicao.header("Authorization", token);
        }
        var resposta = mvc.perform(requisicao.contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(corpo)))
                .andExpect(status().is(statusEsperado)).andReturn().getResponse();
        return json.readTree(resposta.getContentAsByteArray());
    }
}

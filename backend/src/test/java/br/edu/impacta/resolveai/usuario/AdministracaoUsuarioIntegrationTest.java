package br.edu.impacta.resolveai.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import br.edu.impacta.resolveai.security.JwtService;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.usuario.dto.AtualizarUsuarioRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
                        "nome", "Pessoa Atualizada", "email", " NOVO@EXAMPLE.COM ", "perfil", "SOLICITANTE", "versaoEsperada", pessoa.versao()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("novo@example.com"))
                .andExpect(jsonPath("$.perfil").value("SOLICITANTE"));
        mvc.perform(get("/api/usuarios/me").header("Authorization", tokenPessoa)).andExpect(status().isUnauthorized());
        String tokenAntesDeDesativar = login("novo@example.com");
        mvc.perform(get("/api/usuarios/me").header("Authorization", tokenAntesDeDesativar))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("SOLICITANTE"));
        mvc.perform(patch("/api/admin/usuarios/{id}/situacao", id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        mvc.perform(get("/api/usuarios/me").header("Authorization", tokenAntesDeDesativar))
                .andExpect(status().isUnauthorized());
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
        mvc.perform(get("/api/usuarios/me").header("Authorization", tokenAntesDeDesativar))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios/me").header("Authorization", login("novo@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("SOLICITANTE"));
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
                        "nome", "Pessoa", "email", " DUPLICADO@EXAMPLE.COM "))))
                .andExpect(status().isConflict());
        assertThat(outro.email()).isEqualTo("outra@example.com");
    }

    @Test
    void edicaoDesatualizadaNaoRestauraPerfilAdministrativo() throws Exception {
        Usuario primeiro = criar("Primeiro administrador", "primeiro-auditoria@example.com", Perfil.ADMIN);
        Usuario segundo = criar("Segundo administrador", "segundo-auditoria@example.com", Perfil.ADMIN);
        Usuario alvo = criar("Terceiro administrador", "alvo-auditoria@example.com", Perfil.ADMIN);
        String sessaoA = token(primeiro);
        String sessaoB = token(segundo);
        String sessaoAlvo = login(alvo.email());
        long versaoA = versaoObservada(sessaoA, alvo.id());
        long versaoB = versaoObservada(sessaoB, alvo.id());
        assertThat(versaoA).isEqualTo(versaoB);
        mvc.perform(patch("/api/admin/usuarios/{id}", alvo.id()).header("Authorization", sessaoB)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                        "nome", alvo.nome(), "email", alvo.email(), "perfil", "ATENDENTE", "versaoEsperada", versaoB))))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/admin/usuarios/{id}", alvo.id()).header("Authorization", sessaoA)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                        "nome", "Nome atualizado", "email", alvo.email(), "perfil", "ADMIN"))))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/usuarios/{id}", alvo.id()).header("Authorization", sessaoA)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                        "nome", "Nome atualizado", "email", alvo.email()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.perfil").value("ATENDENTE"));
        assertThat(jdbc.queryForMap("SELECT nome, perfil FROM usuarios WHERE id = ?", alvo.id()))
                .containsEntry("nome", "Nome atualizado").containsEntry("perfil", "ATENDENTE");
        mvc.perform(patch("/api/admin/usuarios/{id}", alvo.id()).header("Authorization", sessaoA)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                        "nome", "Nome atualizado", "email", alvo.email(), "perfil", "ADMIN", "versaoEsperada", versaoA))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.violations[0].field").value("versaoEsperada"));
        mvc.perform(get("/api/admin/usuarios").header("Authorization", sessaoAlvo)).andExpect(status().isUnauthorized());
        String sessaoAposRebaixar = login(alvo.email());
        mvc.perform(get("/api/admin/usuarios").header("Authorization", sessaoAposRebaixar)).andExpect(status().isForbidden());
        long atual = versaoObservada(sessaoA, alvo.id());
        mvc.perform(patch("/api/admin/usuarios/{id}", alvo.id()).header("Authorization", sessaoA)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                        "nome", "Nome atualizado", "email", alvo.email(), "perfil", "ADMIN", "versaoEsperada", atual))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT perfil FROM usuarios WHERE id = ?", String.class, alvo.id()))
                .isEqualTo("ADMIN");
        mvc.perform(get("/api/admin/usuarios").header("Authorization", sessaoAlvo)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/usuarios").header("Authorization", sessaoAposRebaixar))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/usuarios").header("Authorization", login(alvo.email()))).andExpect(status().isOk());
    }

    private String login(String email) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", email, "senha", SENHA))))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(result.getResponse().getContentAsByteArray()).path("accessToken").asText();
    }

    private long versaoObservada(String sessao, long id) throws Exception {
        var lista = json.readTree(mvc.perform(get("/api/admin/usuarios").header("Authorization", sessao))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        return java.util.stream.StreamSupport.stream(lista.spliterator(), false)
                .filter(conta -> conta.path("id").asLong() == id)
                .filter(conta -> conta.path("versao").isIntegralNumber())
                .findFirst().orElseThrow().path("versao").asLong();
    }

    @Test
    void revalidaPerfilDoAtorMesmoComPrincipalAntigo() {
        Usuario ator = criar("Pessoa", "ator-antigo@example.com", Perfil.ATENDENTE);
        Usuario alvo = criar("Pessoa", "alvo@example.com", Perfil.SOLICITANTE);
        var principalAntigo = new UsuarioPrincipal(ator.id(), ator.email(), Perfil.ADMIN);
        assertThatThrownBy(() -> administracao.atualizar(alvo.id(), principalAntigo,
                new AtualizarUsuarioRequest("Alterado", alvo.email(), Perfil.ADMIN, alvo.versao())))
                .isInstanceOf(NaoAutorizadoException.class);
        assertThat(alvo.perfil()).isEqualTo(Perfil.SOLICITANTE);
    }

    private Usuario criar(String nome, String email, Perfil perfil) {
        return usuarios.saveAndFlush(Usuario.novo(nome, email, encoder.encode(SENHA), perfil, Instant.now()));
    }

    private String token(Usuario usuario) { return "Bearer " + jwt.generate(usuario); }
}

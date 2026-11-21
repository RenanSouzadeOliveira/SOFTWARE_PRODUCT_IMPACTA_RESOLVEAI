package br.edu.impacta.resolveai.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.ChamadoRepository;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

/** Matriz consolidada de autenticacao, autorizacao, erros padronizados e CORS (P0 seguranca). */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class SegurancaConsolidadaIntegrationTest {
    private static final List<String> TERMOS_PROIBIDOS = List.of("Exception", "at br.", "at org.", "SELECT ", "INSERT ",
            "org.hibernate", "jdbc:", "hash", "Bearer ", "eyJ", "/home/", "stackTrace", "trace");

    @Autowired private MockMvc mvc;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private CategoriaRepository categorias;
    @Autowired private ChamadoRepository chamados;
    @Autowired private JwtService jwt;
    @Autowired private JdbcTemplate jdbc;
    @Value("${app.jwt.secret}") private String secret;

    private Usuario dono, outroSolicitante, atendente, outroAtendente, admin;
    private Categoria categoria;
    private Chamado chamado;

    @BeforeEach void setup() {
        dono = usuario(Perfil.SOLICITANTE);
        outroSolicitante = usuario(Perfil.SOLICITANTE);
        atendente = usuario(Perfil.ATENDENTE);
        outroAtendente = usuario(Perfil.ATENDENTE);
        admin = usuario(Perfil.ADMIN);
        categoria = categorias.save(Categoria.nova("Seguranca " + UUID.randomUUID(), null, Instant.now()));
        chamado = chamados.saveAndFlush(Chamado.abrir("RA-" + UUID.randomUUID().toString().substring(0, 22),
                "Falha de acesso", "Descricao suficientemente longa para o chamado", dono, categoria, Instant.now()));
    }

    // AC-02 — falhas de JWT sempre resultam em 401 seguro.
    @Test void deveRetornar401ParaTodasAsFalhasDeJwt() throws Exception {
        String valido = jwt.generate(dono);
        Map<String, String> cabecalhos = Map.of(
                "malformado", "Bearer nao-e-um-jwt",
                "esquema basic", "Basic dXNlcjpzZW5oYQ==",
                "bearer vazio", "Bearer ",
                "assinatura invalida", "Bearer " + valido.substring(0, valido.lastIndexOf('.') + 1) + "assinaturaforjada",
                "outra chave", "Bearer " + token(dono, Keys.hmacShaKeyFor("outra-chave-com-mais-de-32-bytes-1234".getBytes(StandardCharsets.UTF_8)),
                        "resolveai-api", Instant.now().plusSeconds(600), true, true),
                "expirado", "Bearer " + token(dono, chave(), "resolveai-api", Instant.now().minusSeconds(60), true, true),
                "issuer incorreto", "Bearer " + token(dono, chave(), "outro-emissor", Instant.now().plusSeconds(600), true, true),
                "sem perfil", "Bearer " + token(dono, chave(), "resolveai-api", Instant.now().plusSeconds(600), false, true),
                "sem versao", "Bearer " + token(dono, chave(), "resolveai-api", Instant.now().plusSeconds(600), true, false),
                "alg none", "Bearer " + Jwts.builder().subject(dono.id().toString()).issuer("resolveai-api")
                        .claim("perfil", "ADMIN").claim("versaoSeguranca", 0L)
                        .expiration(Date.from(Instant.now().plusSeconds(600))).compact());
        mvc.perform(get("/api/usuarios/me")).andExpect(status().isUnauthorized());
        for (var caso : cabecalhos.entrySet()) {
            MvcResult resultado = mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, caso.getValue()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Autenticacao necessaria"))
                    .andReturn();
            semVazamento(resultado);
        }
    }

    @Test void deveRevogarTokenAposMudancaDeVersaoOuDesativacao() throws Exception {
        String tokenDono = jwt.generate(dono);
        String tokenAtendente = jwt.generate(atendente);
        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDono)).andExpect(status().isOk());
        jdbc.update("UPDATE usuarios SET versao_seguranca = versao_seguranca + 1 WHERE id = ?", dono.id());
        jdbc.update("UPDATE usuarios SET ativo = FALSE WHERE id = ?", atendente.id());
        mvc.perform(get("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDono)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chamados/" + chamado.id() + "/comentarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDono)
                .contentType(MediaType.APPLICATION_JSON).content("{\"conteudo\":\"Tentativa com token revogado\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/chamados/" + chamado.id() + "/assumir").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAtendente))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comentarios WHERE chamado_id = ?", Long.class, chamado.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM chamados WHERE id = ?", String.class, chamado.id())).isEqualTo("ABERTO");
    }

    // AC-01 — administracao exige ADMIN no servidor.
    @Test void deveNegarAdministracaoParaPerfisNaoAdmin() throws Exception {
        for (Usuario ator : List.of(dono, atendente)) {
            for (RequestBuilder requisicao : List.of(
                    get("/api/admin/usuarios"), get("/api/admin/categorias"), get("/api/atendimento/todos"),
                    post("/api/admin/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"),
                    patch("/api/admin/usuarios/" + ator.id() + "/situacao").contentType(MediaType.APPLICATION_JSON).content("{\"ativo\":false}"),
                    post("/api/admin/categorias").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Indevida\"}"),
                    patch("/api/admin/categorias/" + categoria.id()).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"X\"}"))) {
                MvcResult resultado = mvc.perform(autenticado(requisicao, ator)).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.status").value(403)).andReturn();
                semVazamento(resultado);
            }
        }
        mvc.perform(get("/api/admin/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(autenticado(get("/api/admin/usuarios"), admin)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT ativo FROM usuarios WHERE id = ?", Boolean.class, dono.id())).isTrue();
    }

    // AC-05 — fora do escopo, leitura e escrita respondem 404 identicos ao de chamado inexistente.
    @Test void deveOcultarChamadoForaDoEscopoEmLeiturasEEscritas() throws Exception {
        String base = "/api/chamados/" + chamado.id();
        for (Usuario intruso : List.of(outroSolicitante, atendente, outroAtendente)) {
            for (RequestBuilder requisicao : List.of(get(base), get(base + "/historico"), get(base + "/comentarios"),
                    post(base + "/comentarios").contentType(MediaType.APPLICATION_JSON).content("{\"conteudo\":\"Mensagem indevida\"}"),
                    patch(base + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FECHADO\"}"),
                    patch(base + "/prioridade").contentType(MediaType.APPLICATION_JSON).content("{\"prioridade\":\"ALTA\"}"))) {
                mvc.perform(autenticado(requisicao, intruso)).andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.message").value("Chamado nao encontrado"));
            }
        }
        mvc.perform(autenticado(post("/api/chamados/999999999/comentarios").contentType(MediaType.APPLICATION_JSON)
                .content("{\"conteudo\":\"Mensagem indevida\"}"), outroSolicitante))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Chamado nao encontrado"));
        mvc.perform(autenticado(get(base), dono)).andExpect(status().isOk());
        mvc.perform(autenticado(get(base + "/historico"), admin)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM comentarios WHERE chamado_id = ?", Long.class, chamado.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT prioridade FROM chamados WHERE id = ?", String.class, chamado.id())).isEqualTo("MEDIA");
    }

    @Test void deveRestringirListagensAoEscopoDoAtor() throws Exception {
        mvc.perform(autenticado(get("/api/chamados"), outroSolicitante)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + chamado.id() + ")]").isEmpty());
        mvc.perform(autenticado(get("/api/chamados/exportacao"), outroSolicitante)).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .doesNotContain(chamado.protocolo()));
        mvc.perform(autenticado(get("/api/chamados/exportacao"), dono)).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains(chamado.protocolo()));
        mvc.perform(autenticado(post("/api/chamados").contentType(MediaType.APPLICATION_JSON).content("{}"), atendente))
                .andExpect(status().isForbidden());
    }

    // AC-03 — entradas invalidas retornam 400 com violacoes por campo quando aplicavel.
    @Test void deveRetornar400PadronizadoParaEntradasInvalidas() throws Exception {
        semVazamento(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"email\":\"invalido\",\"senha\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[?(@.field == 'nome')]").isNotEmpty())
                .andExpect(jsonPath("$.violations[?(@.field == 'email')].message").value("email deve ser valido"))
                .andExpect(jsonPath("$.violations[?(@.field == 'senha')]").isNotEmpty()).andReturn());
        semVazamento(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Corpo da requisicao invalido")).andReturn());
        semVazamento(mvc.perform(autenticado(patch("/api/chamados/" + chamado.id() + "/prioridade")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prioridade\":\"URGENTE\"}"), admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations[0].field").value("prioridade")).andReturn());
        semVazamento(mvc.perform(autenticado(get("/api/chamados/abc"), dono)).andExpect(status().isBadRequest()).andReturn());
        mvc.perform(autenticado(get("/api/chamados/0"), dono)).andExpect(status().isBadRequest());
        mvc.perform(autenticado(get("/api/chamados?size=100000"), dono)).andExpect(status().isBadRequest());
        mvc.perform(autenticado(get("/api/chamados?dataInicial=2026-02-01&dataFinal=2026-01-01"), dono)).andExpect(status().isBadRequest());
        semVazamento(mvc.perform(autenticado(post("/api/chamados").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"ab\",\"categoriaId\":-1}"), dono))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[?(@.field == 'titulo')]").isNotEmpty())
                .andExpect(jsonPath("$.violations[?(@.field == 'categoriaId')]").isNotEmpty()).andReturn());
    }

    @Test void deveTratarErrosDoFrameworkSemRespostaInterna() throws Exception {
        semVazamento(mvc.perform(post("/api/auth/login").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415)).andReturn());
        mvc.perform(autenticado(get("/api/chamados/" + chamado.id() + "/historico")
                        .accept(MediaType.APPLICATION_XML), dono))
                .andExpect(status().isNotAcceptable()).andExpect(content().string(""));
        semVazamento(mvc.perform(autenticado(delete("/api/chamados/" + chamado.id()), dono))
                .andExpect(status().isMethodNotAllowed()).andReturn());
        semVazamento(mvc.perform(autenticado(get("/api/inexistente"), dono)).andExpect(status().isNotFound()).andReturn());
    }

    // AC-04 — conflitos conhecidos geram 409 com mensagem funcional.
    @Test void deveRetornar409ParaConflitosConhecidos() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String cadastro = "{\"nome\":\"Pessoa Ficticia\",\"email\":\"" + email + "\",\"senha\":\"SenhaFicticia1\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(cadastro)).andExpect(status().isCreated());
        semVazamento(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(cadastro.replace(email, email.toUpperCase())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("E-mail ja cadastrado")).andReturn());
        semVazamento(mvc.perform(autenticado(post("/api/admin/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  " + categoria.nome().toUpperCase() + " \"}"), admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Já existe uma categoria com esse nome")).andReturn());
        mvc.perform(autenticado(patch("/api/chamados/" + chamado.id() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FECHADO\"}"), dono))
                .andExpect(status().isConflict());
    }

    // AC-07 — CORS aceita somente origens configuradas.
    @Test void deveRestringirCorsAsOrigensConfiguradas() throws Exception {
        mvc.perform(options("/api/chamados").header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        mvc.perform(options("/api/chamados").header(HttpHeaders.ORIGIN, "https://malicioso.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(autenticado(get("/api/usuarios/me"), dono).header(HttpHeaders.ORIGIN, "https://malicioso.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test void naoDeveExporSegredosNasRespostasDeUsuario() throws Exception {
        String corpo = mvc.perform(autenticado(get("/api/admin/usuarios"), admin)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(corpo).doesNotContain("hash-ficticio").doesNotContainIgnoringCase("senha").doesNotContain("versaoSeguranca");
    }

    private void semVazamento(MvcResult resultado) throws Exception {
        String corpo = resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(corpo).as("corpo de erro").startsWith("{").contains("\"status\"");
        for (String termo : TERMOS_PROIBIDOS) assertThat(corpo).as("vazamento de " + termo).doesNotContain(termo);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder autenticado(RequestBuilder requisicao, Usuario ator) {
        var builder = (org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) requisicao;
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.generate(ator));
    }

    private javax.crypto.SecretKey chave() { return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)); }

    private String token(Usuario usuario, javax.crypto.SecretKey chave, String issuer, Instant expiracao,
            boolean comPerfil, boolean comVersao) {
        var builder = Jwts.builder().subject(usuario.id().toString()).issuer(issuer)
                .issuedAt(Date.from(expiracao.minusSeconds(7200))).expiration(Date.from(expiracao));
        if (comPerfil) builder.claim("perfil", usuario.perfil().name());
        if (comVersao) builder.claim("versaoSeguranca", usuario.versaoSeguranca());
        return builder.signWith(chave, Jwts.SIG.HS256).compact();
    }

    private Usuario usuario(Perfil perfil) {
        return usuarios.save(Usuario.novo("Pessoa " + perfil, UUID.randomUUID() + "@example.com", "hash-ficticio", perfil, Instant.now()));
    }
}

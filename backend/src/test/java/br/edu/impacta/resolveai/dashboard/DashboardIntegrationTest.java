package br.edu.impacta.resolveai.dashboard;

import static br.edu.impacta.resolveai.chamado.PrioridadeChamado.ALTA;
import static br.edu.impacta.resolveai.chamado.PrioridadeChamado.BAIXA;
import static br.edu.impacta.resolveai.chamado.PrioridadeChamado.MEDIA;
import static br.edu.impacta.resolveai.chamado.StatusChamado.ABERTO;
import static br.edu.impacta.resolveai.chamado.StatusChamado.EM_ATENDIMENTO;
import static br.edu.impacta.resolveai.chamado.StatusChamado.FECHADO;
import static br.edu.impacta.resolveai.chamado.StatusChamado.RESOLVIDO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.ChamadoRepository;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse.GrupoCategoria;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse.GrupoPrioridade;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse.GrupoStatus;
import br.edu.impacta.resolveai.security.JwtService;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTest {
    private static final String RESUMO = "/api/dashboard/resumo";
    private static final Instant AGORA = Instant.parse("2026-10-01T12:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ChamadoRepository chamados;
    @Autowired JwtService jwt;

    @Test
    void agregaOsMesmosChamadosDaListagemNosTresPerfis() throws Exception {
        var solicitante = usuario(Perfil.SOLICITANTE);
        var outro = usuario(Perfil.SOLICITANTE);
        var atendente = usuario(Perfil.ATENDENTE);
        var outroAtendente = usuario(Perfil.ATENDENTE);
        var admin = usuario(Perfil.ADMIN);
        var antes = resumo(admin);
        var principal = categoria();
        var semChamados = categoria();
        var historica = categoria();
        var oculta = categoria();

        chamado(solicitante, null, principal, BAIXA, ABERTO);
        chamado(solicitante, atendente, principal, ALTA, EM_ATENDIMENTO);
        chamado(solicitante, atendente, historica, MEDIA, RESOLVIDO);
        chamado(outro, atendente, principal, ALTA, FECHADO);
        chamado(outro, null, oculta, BAIXA, ABERTO);
        chamado(outro, outroAtendente, principal, MEDIA, EM_ATENDIMENTO);
        historica.desativar(AGORA);
        oculta.desativar(AGORA);
        categorias.save(historica);
        categorias.save(oculta);

        var meus = verificarConsistencia(solicitante, "/api/chamados/me");
        assertThat(meus.total()).isEqualTo(3);
        assertThat(meus.porStatus()).containsExactly(
                new GrupoStatus(ABERTO, 1), new GrupoStatus(EM_ATENDIMENTO, 1),
                new GrupoStatus(RESOLVIDO, 1), new GrupoStatus(FECHADO, 0));
        assertThat(meus.porPrioridade()).containsExactly(
                new GrupoPrioridade(BAIXA, 1), new GrupoPrioridade(MEDIA, 1), new GrupoPrioridade(ALTA, 1));
        assertThat(meus.porCategoria()).contains(grupo(principal, 2), grupo(historica, 1), grupo(semChamados, 0));
        assertThat(meus.porCategoria()).noneMatch(grupo -> grupo.id().equals(oculta.id()));

        var carteira = verificarConsistencia(atendente, "/api/atendimento/me");
        assertThat(carteira.total()).isEqualTo(3);
        assertThat(carteira.porStatus()).containsExactly(
                new GrupoStatus(ABERTO, 0), new GrupoStatus(EM_ATENDIMENTO, 1),
                new GrupoStatus(RESOLVIDO, 1), new GrupoStatus(FECHADO, 1));
        assertThat(carteira.porPrioridade()).containsExactly(
                new GrupoPrioridade(BAIXA, 0), new GrupoPrioridade(MEDIA, 1), new GrupoPrioridade(ALTA, 2));
        assertThat(carteira.porCategoria()).contains(grupo(principal, 2), grupo(historica, 1), grupo(semChamados, 0));
        assertThat(carteira.porCategoria()).noneMatch(grupo -> grupo.id().equals(oculta.id()));

        var global = verificarConsistencia(admin, "/api/chamados");
        assertThat(global.total() - antes.total()).isEqualTo(6);
        assertThat(global.abertos() - antes.abertos()).isEqualTo(2);
        assertThat(global.emAtendimento() - antes.emAtendimento()).isEqualTo(2);
        assertThat(global.concluidos() - antes.concluidos()).isEqualTo(2);
        assertThat(global.porCategoria()).contains(
                grupo(principal, 4), grupo(historica, 1), grupo(oculta, 1), grupo(semChamados, 0));
    }

    @Test
    void retornaZerosParaEscopoSemChamados() throws Exception {
        var solicitante = usuario(Perfil.SOLICITANTE);
        var ativa = categoria();
        var vazio = verificarConsistencia(solicitante, "/api/chamados/me");
        assertThat(vazio.total()).isZero();
        assertThat(vazio.porStatus()).hasSize(4).allMatch(grupo -> grupo.quantidade() == 0);
        assertThat(vazio.porPrioridade()).hasSize(3).allMatch(grupo -> grupo.quantidade() == 0);
        assertThat(vazio.porCategoria()).contains(grupo(ativa, 0)).allMatch(grupo -> grupo.quantidade() == 0);
    }

    @Test
    void exigeTokenDeUsuarioAtivoERejeitaEscrita() throws Exception {
        mvc.perform(get(RESUMO)).andExpect(status().isUnauthorized());
        mvc.perform(get(RESUMO).header(HttpHeaders.AUTHORIZATION, "Bearer invalido"))
                .andExpect(status().isUnauthorized());

        var solicitante = usuario(Perfil.SOLICITANTE);
        var token = bearer(solicitante);
        var antes = resumo(solicitante);
        mvc.perform(post(RESUMO).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isMethodNotAllowed());
        assertThat(resumo(solicitante)).isEqualTo(antes);

        solicitante.desativar(AGORA);
        usuarios.save(solicitante);
        mvc.perform(get(RESUMO).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isUnauthorized());
    }

    private DashboardResumoResponse verificarConsistencia(Usuario usuario, String listagem) throws Exception {
        var resumo = resumo(usuario);
        var pagina = mvc.perform(get(listagem).param("size", "1").header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(resumo.total()).isEqualTo(json.readTree(pagina.getContentAsByteArray()).get("totalElements").asLong());
        assertThat(resumo.porStatus().stream().mapToLong(GrupoStatus::quantidade).sum()).isEqualTo(resumo.total());
        assertThat(resumo.porPrioridade().stream().mapToLong(GrupoPrioridade::quantidade).sum()).isEqualTo(resumo.total());
        assertThat(resumo.porCategoria().stream().mapToLong(GrupoCategoria::quantidade).sum()).isEqualTo(resumo.total());
        assertThat(resumo.abertos()).isEqualTo(quantidade(resumo, ABERTO));
        assertThat(resumo.emAtendimento()).isEqualTo(quantidade(resumo, EM_ATENDIMENTO));
        assertThat(resumo.concluidos()).isEqualTo(quantidade(resumo, RESOLVIDO) + quantidade(resumo, FECHADO));
        return resumo;
    }

    private long quantidade(DashboardResumoResponse resumo, StatusChamado status) {
        return resumo.porStatus().stream().filter(grupo -> grupo.status() == status)
                .findFirst().orElseThrow().quantidade();
    }

    private DashboardResumoResponse resumo(Usuario usuario) throws Exception {
        var resposta = mvc.perform(get(RESUMO).header(HttpHeaders.AUTHORIZATION, bearer(usuario)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readValue(resposta.getContentAsByteArray(), DashboardResumoResponse.class);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwt.generate(usuario);
    }

    private Usuario usuario(Perfil perfil) {
        return usuarios.save(Usuario.novo("Pessoa fictícia", UUID.randomUUID() + "@example.invalid",
                "hash-de-teste", perfil, AGORA));
    }

    private Categoria categoria() {
        return categorias.save(Categoria.nova("Categoria fictícia " + UUID.randomUUID(), null, AGORA));
    }

    private GrupoCategoria grupo(Categoria categoria, long quantidade) {
        return new GrupoCategoria(categoria.id(), categoria.nome(), quantidade);
    }

    private void chamado(Usuario solicitante, Usuario atendente, Categoria categoria,
            PrioridadeChamado prioridade, StatusChamado status) {
        var chamado = Chamado.abrir("D-" + UUID.randomUUID().toString().substring(0, 24), "Chamado fictício",
                "Descrição fictícia do chamado", prioridade, solicitante, categoria, AGORA);
        if (atendente != null) {
            chamado.assumir(atendente, AGORA.plusSeconds(1));
        }
        if (status == RESOLVIDO || status == FECHADO) {
            chamado.alterarStatus(atendente, RESOLVIDO, "Solução fictícia registrada", AGORA.plusSeconds(2));
        }
        if (status == FECHADO) {
            chamado.alterarStatus(solicitante, FECHADO, null, AGORA.plusSeconds(3));
        }
        chamados.saveAndFlush(chamado);
    }
}

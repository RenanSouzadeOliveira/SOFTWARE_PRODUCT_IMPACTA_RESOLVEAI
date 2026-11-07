package br.edu.impacta.resolveai.shared.logging;

import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import br.edu.impacta.resolveai.chamado.FiltrosChamados;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

/** Apenas observabilidade; não consulta dados nem altera filtros ou permissões. */
public final class OperacoesChamadosLog {
    private static final Logger LOG = LoggerFactory.getLogger(OperacoesChamadosLog.class);
    private OperacoesChamadosLog() { }

    public static String escopo(UsuarioPrincipal principal) {
        return switch (principal.perfil()) {
            case SOLICITANTE -> "SOLICITANTE_PROPRIOS";
            case ATENDENTE -> "ATENDENTE_ATRIBUIDOS";
            case ADMIN -> "ADMIN_GLOBAL";
        };
    }

    public static long inicio(String operacao, FiltrosChamados filtros, Sort ordem, String escopo) {
        long inicio = System.nanoTime();
        if (LOG.isInfoEnabled())
            LOG.info("[{}] INICIO requisicaoId={} escopo={} filtros={} ordenacao={} repositorio=ChamadoRepository.findAll(Specification,Pageable)",
                    operacao, requisicaoId(), escopo, filtrosAtivos(filtros),
                    ordem.stream().map(o -> o.getProperty() + "," + o.getDirection()).collect(Collectors.joining(";")));
        return inicio;
    }

    private static String filtrosAtivos(FiltrosChamados filtros) {
        var ativos = new StringJoiner(" ", "[", "]").setEmptyValue("nenhum");
        adicionarFiltro(ativos, "status", filtros.status());
        adicionarFiltro(ativos, "prioridade", filtros.prioridade());
        adicionarFiltro(ativos, "categoriaId", filtros.categoriaId());
        adicionarFiltro(ativos, "responsavelId", filtros.responsavel());
        adicionarFiltro(ativos, "dataInicial", filtros.dataInicial());
        adicionarFiltro(ativos, "dataFinal", filtros.dataFinal());
        if (filtros.search() != null && !filtros.search().isBlank()) {
            ativos.add("pesquisaAtiva=true");
        }
        return ativos.toString();
    }

    private static void adicionarFiltro(StringJoiner ativos, String campo, Object valor) {
        if (valor != null) {
            ativos.add(campo + "=" + valor);
        }
    }

    public static void resultado(Page<?> pagina, long inicio) {
        LOG.info("[CHAMADOS_CONSULTA] RESULTADO requisicaoId={} pagina={} tamanho={} totalElementos={} totalPaginas={} registrosRetornados={} paginaVazia={} duracaoMs={}",
                requisicaoId(), pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(),
                pagina.getTotalPages(), pagina.getNumberOfElements(), pagina.isEmpty(), duracao(inicio));
    }

    public static void exportacaoContagem(long total, int limite) {
        LOG.info("[CHAMADOS_EXPORTACAO] CONTAGEM requisicaoId={} totalElementos={} limite={}", requisicaoId(), total, limite);
    }

    public static void exportacaoConcluida(long total, long gerados, long inicio) {
        LOG.info("[CHAMADOS_EXPORTACAO] CONCLUIDA requisicaoId={} totalElementos={} registrosExportados={} duracaoMs={}",
                requisicaoId(), total, gerados, duracao(inicio));
    }

    public static void exportacaoFalha(String motivo, long total, long gerados, long inicio) {
        boolean falha = "EXECUCAO_FALHOU".equals(motivo);
        LOG.atLevel(falha ? Level.WARN : Level.INFO).log(
                "[CHAMADOS_EXPORTACAO] {} requisicaoId={} motivo={} totalElementos={} registrosGerados={} duracaoMs={}",
                falha ? "FALHOU" : "REJEITADA", requisicaoId(), motivo, total, gerados, duracao(inicio));
    }

    public static void textoDemo(String operacao, String search) {
        // Somente marcador público conhecido: títulos, descrições e texto livre nunca entram no log.
        if (search != null && search.length() <= 80
                && search.strip().matches("(?i)\\[?AC3-ACADEMICO-V1\\]?(?: [0-9]{1,3})?"))
            LOG.info("[{}] TEXTO_DEMO requisicaoId={} textoFicticio={}", operacao, requisicaoId(),
                    search.strip().substring(0, Math.min(search.strip().length(), 40)));
    }

    private static String requisicaoId() {
        String id = MDC.get("requisicaoId");
        return id == null ? "fora_http" : id;
    }
    private static long duracao(long inicio) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio); }
}

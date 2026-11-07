package br.edu.impacta.resolveai.atendimento;

import java.time.Clock;
import br.edu.impacta.resolveai.shared.logging.OperacoesChamadosLog;
import java.time.Instant;
import java.util.List;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.atendimento.dto.CategoriaAtendimentoResponse;

import br.edu.impacta.resolveai.atendimento.dto.ChamadoAtendimentoResponse;
import br.edu.impacta.resolveai.atendimento.dto.PaginaAtendimentoResponse;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.ChamadoRepository;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.chamado.HistoricoStatus;
import br.edu.impacta.resolveai.chamado.HistoricoStatusRepository;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.shared.error.RecursoNaoEncontradoException;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AtendimentoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AtendimentoService.class);

    private final ChamadoRepository chamados;
    private final UsuarioRepository usuarios;
    private final HistoricoAtribuicaoRepository historico;
    private final Clock clock;
    private final CategoriaRepository categorias;
    private final HistoricoStatusRepository historicoStatus;

    public AtendimentoService(ChamadoRepository chamados, UsuarioRepository usuarios,
            HistoricoAtribuicaoRepository historico, Clock clock, CategoriaRepository categorias,
            HistoricoStatusRepository historicoStatus) {
        this.chamados = chamados;
        this.usuarios = usuarios;
        this.historico = historico;
        this.clock = clock;
        this.categorias = categorias;
        this.historicoStatus = historicoStatus;
    }

    @Transactional(readOnly = true)
    public List<CategoriaAtendimentoResponse> categorias(UsuarioPrincipal principal) {
        usuarioAutorizado(principal);
        return categorias.findAllByOrderByNomeAscIdAsc().stream()
                .map(CategoriaAtendimentoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PaginaAtendimentoResponse fila(UsuarioPrincipal principal, Pageable pageable,
            PrioridadeChamado prioridade, Long categoriaId) {
        usuarioAutorizado(principal);
        return PaginaAtendimentoResponse.from(chamados.findFila(prioridade, categoriaId,
                pagina(pageable, categoriaId)));
    }

    @Transactional(readOnly = true)
    public PaginaAtendimentoResponse carteira(UsuarioPrincipal principal, Pageable pageable,
            PrioridadeChamado prioridade, Long categoriaId, StatusChamado status) {
        Usuario usuario = usuarioAutorizado(principal);
        return PaginaAtendimentoResponse.from(chamados.findCarteira(usuario.id(), status, prioridade,
                categoriaId, pagina(pageable, categoriaId)));
    }

    @Transactional(readOnly = true)
    public PaginaAtendimentoResponse listarFiltrados(UsuarioPrincipal principal, Pageable pageable,
            br.edu.impacta.resolveai.chamado.FiltrosChamados filtros, boolean carteira) {
        Usuario usuario = usuarioAutorizado(principal);
        var visibilidade = carteira ? br.edu.impacta.resolveai.chamado.VisibilidadeChamados.carteira(usuario.id())
                : br.edu.impacta.resolveai.chamado.VisibilidadeChamados.fila();
        long inicio = OperacoesChamadosLog.inicio("CHAMADOS_CONSULTA", filtros, pageable.getSort(),
                carteira ? "CARTEIRA_PROPRIA" : "FILA_ABERTA_SEM_RESPONSAVEL");
        var pagina = chamados.findAll(visibilidade.and(filtros.specification()), pageable);
        var response = PaginaAtendimentoResponse.from(pagina);
        OperacoesChamadosLog.resultado(pagina, inicio);
        return response;
    }

    @Transactional
    public ChamadoAtendimentoResponse assumir(Long id, UsuarioPrincipal principal) {
        Usuario usuario = usuarioAutorizado(principal);
        if (id == null || id < 1) {
            throw new DadosInvalidosException("Identificador do chamado deve ser positivo");
        }
        Chamado chamado = chamados.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado nao encontrado"));
        Instant agora = Instant.now(clock);
        chamado.assumir(usuario, agora);
        // Flush verifica @Version antes de registrar o evento; conflito reverte toda a transacao.
        chamados.saveAndFlush(chamado);
        historico.save(HistoricoAtribuicao.registrar(chamado, usuario, agora));
        historicoStatus.save(HistoricoStatus.registrar(chamado, StatusChamado.ABERTO, usuario, agora, null));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                LOGGER.info("AUDITORIA evento=CHAMADO_ASSUMIDO chamadoId={} atendenteId={} status=EM_ATENDIMENTO",
                        chamado.id(), usuario.id());
            }
        });
        return ChamadoAtendimentoResponse.from(chamado);
    }

    @Transactional(readOnly = true)
    public PaginaAtendimentoResponse todos(UsuarioPrincipal principal, Pageable pageable,
            br.edu.impacta.resolveai.chamado.FiltrosChamados filtros) {
        Usuario usuario = usuarioAutorizado(principal);
        if (usuario.perfil() != Perfil.ADMIN)
            throw new AccessDeniedException("Consulta de todos os chamados exige perfil ADMIN");
        long inicio = OperacoesChamadosLog.inicio("CHAMADOS_CONSULTA", filtros, pageable.getSort(), "ADMIN_GLOBAL");
        var pagina = chamados.findAll(filtros.specification(), pageable);
        var response = PaginaAtendimentoResponse.from(pagina);
        OperacoesChamadosLog.resultado(pagina, inicio);
        return response;
    }

    private Usuario usuarioAutorizado(UsuarioPrincipal principal) {
        if (principal == null) {
            throw new NaoAutorizadoException("Autenticacao necessaria");
        }
        Usuario usuario = usuarios.findById(principal.id()).filter(Usuario::ativo)
                .orElseThrow(() -> new NaoAutorizadoException("Conta inexistente ou inativa"));
        if (usuario.perfil() != Perfil.ATENDENTE && usuario.perfil() != Perfil.ADMIN) {
            throw new AccessDeniedException("Perfil autenticado sem permissao");
        }
        return usuario;
    }

    private Pageable pagina(Pageable pageable, Long categoriaId) {
        if (categoriaId != null && categoriaId < 1) {
            throw new DadosInvalidosException("categoriaId deve ser positivo");
        }
        return pageable;
    }
}

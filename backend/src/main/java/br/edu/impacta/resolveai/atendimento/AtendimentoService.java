package br.edu.impacta.resolveai.atendimento;

import java.time.Clock;
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
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.shared.error.RecursoNaoEncontradoException;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    public AtendimentoService(ChamadoRepository chamados, UsuarioRepository usuarios,
            HistoricoAtribuicaoRepository historico, Clock clock, CategoriaRepository categorias) {
        this.chamados = chamados;
        this.usuarios = usuarios;
        this.historico = historico;
        this.clock = clock;
        this.categorias = categorias;
    }

    @Transactional(readOnly = true)
    public List<CategoriaAtendimentoResponse> categorias(UsuarioPrincipal principal) {
        usuarioAutorizado(principal);
        return categorias.findAllByOrderByNomeAscIdAsc().stream()
                .map(CategoriaAtendimentoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PaginaAtendimentoResponse fila(UsuarioPrincipal principal, int page, int size,
            PrioridadeChamado prioridade, Long categoriaId) {
        usuarioAutorizado(principal);
        return PaginaAtendimentoResponse.from(chamados.findFila(prioridade, categoriaId,
                pagina(page, size, categoriaId)));
    }

    @Transactional(readOnly = true)
    public PaginaAtendimentoResponse carteira(UsuarioPrincipal principal, int page, int size,
            PrioridadeChamado prioridade, Long categoriaId, StatusChamado status) {
        Usuario usuario = usuarioAutorizado(principal);
        return PaginaAtendimentoResponse.from(chamados.findCarteira(usuario.id(), status, prioridade,
                categoriaId, pagina(page, size, categoriaId)));
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
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                LOGGER.info("AUDITORIA evento=CHAMADO_ASSUMIDO chamadoId={} atendenteId={} status=EM_ATENDIMENTO",
                        chamado.id(), usuario.id());
            }
        });
        return ChamadoAtendimentoResponse.from(chamado);
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

    private PageRequest pagina(int page, int size, Long categoriaId) {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > Integer.MAX_VALUE
                || (categoriaId != null && categoriaId < 1)) {
            throw new DadosInvalidosException("Pagina deve ser >= 0, tamanho entre 1 e 50 e categoriaId positivo");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.desc("id")));
    }
}

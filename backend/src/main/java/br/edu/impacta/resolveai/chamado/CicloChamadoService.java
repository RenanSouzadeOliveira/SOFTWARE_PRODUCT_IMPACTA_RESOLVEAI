package br.edu.impacta.resolveai.chamado;

import java.time.Clock;
import java.time.Instant;
import br.edu.impacta.resolveai.atendimento.HistoricoAtribuicao;
import br.edu.impacta.resolveai.atendimento.HistoricoAtribuicaoRepository;
import br.edu.impacta.resolveai.chamado.dto.AlterarPrioridadeRequest;
import br.edu.impacta.resolveai.chamado.dto.AlterarStatusRequest;
import br.edu.impacta.resolveai.chamado.dto.ChamadoDetalheResponse;
import br.edu.impacta.resolveai.chamado.dto.HistoricoStatusResponse;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.shared.error.RecursoNaoEncontradoException;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CicloChamadoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CicloChamadoService.class);
    private final ChamadoRepository chamados;
    private final UsuarioRepository usuarios;
    private final HistoricoStatusRepository historicoStatus;
    private final HistoricoAtribuicaoRepository historicoAlteracoes;
    private final Clock clock;

    public CicloChamadoService(ChamadoRepository chamados, UsuarioRepository usuarios,
            HistoricoStatusRepository historicoStatus, HistoricoAtribuicaoRepository historicoAlteracoes, Clock clock) {
        this.chamados = chamados;
        this.usuarios = usuarios;
        this.historicoStatus = historicoStatus;
        this.historicoAlteracoes = historicoAlteracoes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ChamadoDetalheResponse detalhar(Long id, UsuarioPrincipal principal) {
        Usuario usuario = usuarioAtivo(principal);
        Chamado chamado = buscar(id);
        if (!MaquinaEstadosChamado.podeConsultar(chamado, usuario))
            throw new RecursoNaoEncontradoException("Chamado nao encontrado");
        return resposta(chamado, usuario);
    }

    @Transactional
    public ChamadoDetalheResponse alterarStatus(Long id, UsuarioPrincipal principal, AlterarStatusRequest request) {
        Usuario usuario = usuarioAtivo(principal);
        Chamado chamado = buscar(id);
        StatusChamado anterior = chamado.status();
        Instant agora = Instant.now(clock);
        String texto = chamado.alterarStatus(usuario, request.status(), request.justificativa(), agora);
        chamados.saveAndFlush(chamado);
        historicoStatus.save(HistoricoStatus.registrar(chamado, anterior, usuario, agora, texto));
        auditar("STATUS", chamado.id(), usuario.id());
        return resposta(chamado, usuario);
    }

    @Transactional
    public ChamadoDetalheResponse alterarPrioridade(Long id, UsuarioPrincipal principal, AlterarPrioridadeRequest request) {
        Usuario usuario = usuarioAtivo(principal);
        Chamado chamado = buscar(id);
        PrioridadeChamado anterior = chamado.prioridade();
        Instant agora = Instant.now(clock);
        chamado.alterarPrioridade(usuario, request.prioridade(), agora);
        chamados.saveAndFlush(chamado);
        historicoAlteracoes.save(HistoricoAtribuicao.registrarPrioridade(chamado, anterior, usuario, agora));
        auditar("PRIORIDADE", chamado.id(), usuario.id());
        return resposta(chamado, usuario);
    }

    private ChamadoDetalheResponse resposta(Chamado chamado, Usuario usuario) {
        return ChamadoDetalheResponse.from(chamado, MaquinaEstadosChamado.acoesPermitidas(chamado, usuario),
                historicoStatus.findByChamadoIdOrderByCriadoEmAscIdAsc(chamado.id()).stream()
                        .map(HistoricoStatusResponse::from).toList());
    }

    private Chamado buscar(Long id) {
        if (id == null || id < 1) throw new DadosInvalidosException("Identificador do chamado deve ser positivo");
        return chamados.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Chamado nao encontrado"));
    }

    private Usuario usuarioAtivo(UsuarioPrincipal principal) {
        if (principal == null) throw new NaoAutorizadoException("Autenticacao necessaria");
        return usuarios.findById(principal.id()).filter(Usuario::ativo)
                .orElseThrow(() -> new NaoAutorizadoException("Conta inexistente ou inativa"));
    }

    private void auditar(String evento, Long chamadoId, Long autorId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                LOGGER.info("AUDITORIA evento=CHAMADO_{} chamadoId={} autorId={}", evento, chamadoId, autorId);
            }
        });
    }
}

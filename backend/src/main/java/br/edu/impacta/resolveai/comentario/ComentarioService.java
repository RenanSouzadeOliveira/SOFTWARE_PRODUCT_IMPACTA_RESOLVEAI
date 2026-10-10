package br.edu.impacta.resolveai.comentario;

import java.time.Clock;
import java.time.Instant;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.chamado.ChamadoRepository;
import br.edu.impacta.resolveai.chamado.MaquinaEstadosChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.comentario.dto.ComentarioResponse;
import br.edu.impacta.resolveai.comentario.dto.ComentariosResponse;
import br.edu.impacta.resolveai.comentario.dto.CriarComentarioRequest;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ConflitoException;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.shared.error.RecursoNaoEncontradoException;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ComentarioService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComentarioService.class);
    private final ComentarioRepository comentarios;
    private final ChamadoRepository chamados;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public ComentarioService(ComentarioRepository comentarios, ChamadoRepository chamados,
            UsuarioRepository usuarios, Clock clock) {
        this.comentarios = comentarios;
        this.chamados = chamados;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ComentariosResponse listar(Long chamadoId, UsuarioPrincipal principal) {
        Usuario usuario = usuarioAtivo(principal);
        validarId(chamadoId);
        Chamado chamado = chamados.findById(chamadoId).orElseThrow(this::naoEncontrado);
        if (!MaquinaEstadosChamado.podeConsultar(chamado, usuario)) throw naoEncontrado();
        return new ComentariosResponse(comentarios.findByChamadoIdOrderByCriadoEmAscIdAsc(chamadoId)
                .stream().map(ComentarioResponse::from).toList(), chamado.status() != StatusChamado.FECHADO);
    }

    @Transactional
    public ComentarioResponse criar(Long chamadoId, UsuarioPrincipal principal, CriarComentarioRequest request) {
        Usuario usuario = usuarioAtivo(principal);
        validarId(chamadoId);
        // Locks the ticket until commit. Closing uses an UPDATE on the same row, so it
        // serializes with comment creation without altering the ticket's version/dates.
        Chamado chamado = chamados.findByIdForComment(chamadoId).orElseThrow(this::naoEncontrado);
        if (!MaquinaEstadosChamado.podeConsultar(chamado, usuario))
            throw new AccessDeniedException("Usuario nao participa deste chamado");
        if (chamado.status() == StatusChamado.FECHADO)
            throw new ConflitoException("Chamado fechado nao permite novos comentarios");
        Comentario comentario = comentarios.save(Comentario.criar(chamado, usuario, request.conteudo(), Instant.now(clock)));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                LOGGER.info("AUDITORIA evento=COMENTARIO_CRIADO comentarioId={} chamadoId={} autorId={}",
                        comentario.id(), chamadoId, usuario.id());
            }
        });
        return ComentarioResponse.from(comentario);
    }

    private Usuario usuarioAtivo(UsuarioPrincipal principal) {
        if (principal == null) throw new NaoAutorizadoException("Autenticacao necessaria");
        return usuarios.findById(principal.id()).filter(Usuario::ativo)
                .orElseThrow(() -> new NaoAutorizadoException("Conta inexistente ou inativa"));
    }
    private void validarId(Long id) {
        if (id == null || id < 1) throw new DadosInvalidosException("Identificador do chamado deve ser positivo");
    }
    private RecursoNaoEncontradoException naoEncontrado() {
        return new RecursoNaoEncontradoException("Chamado nao encontrado");
    }
}

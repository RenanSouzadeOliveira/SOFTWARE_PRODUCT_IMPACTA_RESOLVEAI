package br.edu.impacta.resolveai.usuario;

import java.time.Clock;
import java.util.List;

import br.edu.impacta.resolveai.auth.AuthService;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ConflitoException;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.shared.error.RecursoNaoEncontradoException;
import br.edu.impacta.resolveai.usuario.dto.AlterarSituacaoUsuarioRequest;
import br.edu.impacta.resolveai.usuario.dto.AtualizarUsuarioRequest;
import br.edu.impacta.resolveai.usuario.dto.CriarUsuarioRequest;
import br.edu.impacta.resolveai.usuario.dto.UsuarioResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministracaoUsuarioService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdministracaoUsuarioService.class);
    private final UsuarioRepository usuarios;
    private final UsuarioService usuarioService;
    private final AuthService authService;
    private final Clock clock;

    public AdministracaoUsuarioService(UsuarioRepository usuarios, UsuarioService usuarioService,
            AuthService authService, Clock clock) {
        this.usuarios = usuarios;
        this.usuarioService = usuarioService;
        this.authService = authService;
        this.clock = clock;
    }

    @Transactional
    public List<UsuarioResponse> listar(UsuarioPrincipal principal) {
        usuarioService.requireAdmin(principal);
        return usuarios.findAllByOrderByNomeAscIdAsc().stream().map(UsuarioResponse::from).toList();
    }

    @Transactional
    public UsuarioResponse criar(UsuarioPrincipal principal, CriarUsuarioRequest request) {
        usuarioService.requireAdmin(principal);
        return authService.cadastrar(request.nome(), request.email(), request.senha(), request.perfil());
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioPrincipal principal, AtualizarUsuarioRequest request) {
        Usuario usuario = bloquearAlvoEValidarAdmin(id, principal);
        if (id.equals(principal.id()) && request.perfil() != Perfil.ADMIN) {
            throw new AccessDeniedException("Não é permitido remover o próprio perfil ADMIN");
        }
        usuarios.findByEmail(request.email()).filter(outro -> !outro.id().equals(id)).ifPresent(outro -> {
            throw new ConflitoException("E-mail já cadastrado");
        });
        usuario.atualizarCadastro(request.nome(), request.email(), request.perfil(), clock.instant());
        try {
            Usuario salvo = usuarios.saveAndFlush(usuario);
            LOGGER.info("AUDITORIA evento=USUARIO_ATUALIZADO atorId={} usuarioId={} perfil={}",
                    principal.id(), id, salvo.perfil());
            return UsuarioResponse.from(salvo);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflitoException("E-mail já cadastrado");
        }
    }

    @Transactional
    public UsuarioResponse alterarSituacao(Long id, UsuarioPrincipal principal,
            AlterarSituacaoUsuarioRequest request) {
        Usuario usuario = bloquearAlvoEValidarAdmin(id, principal);
        if (id.equals(principal.id()) && !request.ativo()) {
            throw new AccessDeniedException("Não é permitido desativar a própria conta");
        }
        usuario.alterarSituacao(request.ativo(), clock.instant());
        Usuario salvo = usuarios.saveAndFlush(usuario);
        LOGGER.info("AUDITORIA evento=USUARIO_SITUACAO_ALTERADA atorId={} usuarioId={} ativo={}",
                principal.id(), id, salvo.ativo());
        return UsuarioResponse.from(salvo);
    }

    private Usuario bloquearAlvoEValidarAdmin(Long id, UsuarioPrincipal principal) {
        if (principal == null) {
            throw new NaoAutorizadoException("Autenticação necessária");
        }
        if (id == null || id < 1) {
            throw new DadosInvalidosException("Identificador do usuário deve ser positivo");
        }
        // A ordem evita deadlock quando dois administradores alteram um ao outro.
        long primeiro = Math.min(id, principal.id());
        long segundo = Math.max(id, principal.id());
        Usuario menor = usuarios.findByIdParaAtualizacao(primeiro).orElse(null);
        Usuario maior = primeiro == segundo ? menor : usuarios.findByIdParaAtualizacao(segundo).orElse(null);
        Usuario ator = principal.id() == primeiro ? menor : maior;
        UsuarioService.validarAdmin(ator);
        Usuario alvo = id == primeiro ? menor : maior;
        if (alvo == null) {
            throw new RecursoNaoEncontradoException("Usuário não encontrado");
        }
        return alvo;
    }
}

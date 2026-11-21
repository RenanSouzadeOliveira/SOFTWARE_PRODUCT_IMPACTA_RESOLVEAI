package br.edu.impacta.resolveai.usuario;

import java.time.Clock;
import java.util.List;

import br.edu.impacta.resolveai.auth.AuthService;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ConflitoException;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.FieldViolation;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.shared.error.RecursoNaoEncontradoException;
import br.edu.impacta.resolveai.usuario.dto.AlterarSituacaoUsuarioRequest;
import br.edu.impacta.resolveai.usuario.dto.AtualizarUsuarioRequest;
import br.edu.impacta.resolveai.usuario.dto.CriarUsuarioRequest;
import br.edu.impacta.resolveai.usuario.dto.UsuarioResponse;
import br.edu.impacta.resolveai.shared.logging.AuditoriaAdministrativa;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministracaoUsuarioService {
    private final UsuarioRepository usuarios;
    private final UsuarioService usuarioService;
    private final AuthService authService;
    private final Clock clock;
    private final AuditoriaAdministrativa auditoria;

    public AdministracaoUsuarioService(UsuarioRepository usuarios, UsuarioService usuarioService,
            AuthService authService, Clock clock, AuditoriaAdministrativa auditoria) {
        this.usuarios = usuarios;
        this.usuarioService = usuarioService;
        this.authService = authService;
        this.clock = clock;
        this.auditoria = auditoria;
    }

    @Transactional
    public List<UsuarioResponse> listar(UsuarioPrincipal principal) {
        usuarioService.requireAdmin(principal);
        return usuarios.findAllByOrderByNomeAscIdAsc().stream().map(UsuarioResponse::from).toList();
    }

    @Transactional
    public UsuarioResponse criar(UsuarioPrincipal principal, CriarUsuarioRequest request) {
        usuarioService.requireAdmin(principal);
        var criado = authService.cadastrar(request.nome(), request.email(), request.senha(), request.perfil());
        auditoria.usuario("USUARIO_CRIADO_POR_ADMIN", principal.id(), criado);
        return criado;
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioPrincipal principal, AtualizarUsuarioRequest request) {
        Usuario usuario = bloquearAlvoEValidarAdmin(id, principal);
        Perfil perfil = request.perfil() == null ? usuario.perfil() : request.perfil();
        if (id.equals(principal.id()) && perfil != Perfil.ADMIN) {
            throw new AccessDeniedException("Não é permitido remover o próprio perfil ADMIN");
        }
        if (request.perfil() != null && request.versaoEsperada() == null) {
            throw new DadosInvalidosException("Informe a versão do usuário para alterar o perfil");
        }
        if (request.versaoEsperada() != null && request.versaoEsperada() != usuario.versao()) {
            String mensagem = "O usuário foi atualizado. Revise os dados antes de tentar novamente.";
            throw new ConflitoException(mensagem, List.of(new FieldViolation("versaoEsperada", mensagem)));
        }
        usuarios.findByEmail(request.email()).filter(outro -> !outro.id().equals(id)).ifPresent(outro -> {
            throw new ConflitoException("E-mail já cadastrado");
        });
        usuario.atualizarCadastro(request.nome(), request.email(), perfil, clock.instant());
        try {
            var salvo = UsuarioResponse.from(usuarios.saveAndFlush(usuario));
            auditoria.usuario("USUARIO_ATUALIZADO", principal.id(), salvo);
            return salvo;
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
        var salvo = UsuarioResponse.from(usuarios.saveAndFlush(usuario));
        auditoria.usuario("USUARIO_SITUACAO_ALTERADA", principal.id(), salvo);
        return salvo;
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
        UsuarioService.validarAdmin(UsuarioService.validarSessao(ator, principal));
        Usuario alvo = id == primeiro ? menor : maior;
        if (alvo == null) {
            throw new RecursoNaoEncontradoException("Usuário não encontrado");
        }
        return alvo;
    }
}

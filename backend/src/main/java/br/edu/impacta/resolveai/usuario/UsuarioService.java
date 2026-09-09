package br.edu.impacta.resolveai.usuario;

import java.time.Clock;

import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.usuario.dto.AtualizarNomeRequest;
import br.edu.impacta.resolveai.usuario.dto.UsuarioResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public UsuarioService(UsuarioRepository usuarioRepository, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UsuarioResponse me(UsuarioPrincipal principal) {
        return UsuarioResponse.from(requireActive(principal.id()));
    }

    @Transactional
    public UsuarioResponse updateName(UsuarioPrincipal principal, AtualizarNomeRequest request) {
        Usuario usuario = requireActive(principal.id());
        usuario.alterarNome(request.nome(), clock.instant());
        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    private Usuario requireActive(Long id) {
        return usuarioRepository.findById(id)
                .filter(Usuario::ativo)
                .orElseThrow(() -> new NaoAutorizadoException("Autenticacao necessaria"));
    }
}

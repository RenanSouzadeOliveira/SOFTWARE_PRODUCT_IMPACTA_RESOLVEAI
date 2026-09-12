package br.edu.impacta.resolveai.usuario;

import java.time.Clock;

import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.usuario.dto.AtualizarNomeRequest;
import br.edu.impacta.resolveai.usuario.dto.UsuarioResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsuarioService.class);

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
        Usuario saved = usuarioRepository.save(usuario);
        LOGGER.info(
                "AUDITORIA evento=CONTA_ATUALIZADA usuarioId={} campos=nome",
                saved.id());
        return UsuarioResponse.from(saved);
    }

    private Usuario requireActive(Long id) {
        return usuarioRepository.findById(id)
                .filter(Usuario::ativo)
                .orElseThrow(() -> new NaoAutorizadoException("Autenticacao necessaria"));
    }
}

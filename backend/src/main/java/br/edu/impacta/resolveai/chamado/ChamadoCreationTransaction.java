package br.edu.impacta.resolveai.chamado;

import java.time.Clock;
import java.time.Instant;

import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.dto.AbrirChamadoRequest;
import br.edu.impacta.resolveai.chamado.dto.ChamadoResponse;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class ChamadoCreationTransaction {

    private final ChamadoRepository chamadoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    ChamadoCreationTransaction(
            ChamadoRepository chamadoRepository,
            CategoriaRepository categoriaRepository,
            UsuarioRepository usuarioRepository,
            Clock clock) {
        this.chamadoRepository = chamadoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChamadoResponse criar(Long solicitanteId, AbrirChamadoRequest request, String protocolo) {
        Usuario solicitante = usuarioRepository.findById(solicitanteId)
                .filter(Usuario::ativo)
                .filter(usuario -> usuario.perfil() == Perfil.SOLICITANTE)
                .orElseThrow(() -> new NaoAutorizadoException("Usuario autenticado nao pode abrir chamados"));
        Categoria categoria = categoriaRepository.findByIdAndAtivaTrue(request.categoriaId())
                .orElseThrow(() -> new DadosInvalidosException("Categoria inexistente ou inativa"));
        Instant agora = clock.instant();
        Chamado chamado = Chamado.abrir(
                protocolo,
                request.titulo(),
                request.descricao(),
                request.prioridade(),
                solicitante,
                categoria,
                agora);
        Chamado salvo = chamadoRepository.saveAndFlush(chamado);
        return ChamadoResponse.from(salvo);
    }
}

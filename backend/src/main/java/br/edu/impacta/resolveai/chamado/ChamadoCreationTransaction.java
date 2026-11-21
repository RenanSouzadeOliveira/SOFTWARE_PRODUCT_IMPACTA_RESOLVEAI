package br.edu.impacta.resolveai.chamado;

import java.time.Clock;
import java.time.Instant;
import br.edu.impacta.resolveai.atendimento.HistoricoAtribuicao;
import br.edu.impacta.resolveai.atendimento.HistoricoAtribuicaoRepository;

import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.dto.AbrirChamadoRequest;
import br.edu.impacta.resolveai.chamado.dto.ChamadoResponse;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioService;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.FieldViolation;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class ChamadoCreationTransaction {

    private final ChamadoRepository chamadoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioService usuarioService;
    private final Clock clock;
    private final HistoricoAtribuicaoRepository historico;

    ChamadoCreationTransaction(
            ChamadoRepository chamadoRepository,
            CategoriaRepository categoriaRepository,
            UsuarioService usuarioService,
            Clock clock, HistoricoAtribuicaoRepository historico) {
        this.chamadoRepository = chamadoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioService = usuarioService;
        this.clock = clock;
        this.historico = historico;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChamadoResponse criar(UsuarioPrincipal principal, AbrirChamadoRequest request, String protocolo) {
        Usuario solicitante = usuarioService.requireActiveForWrite(principal);
        if (solicitante.perfil() != Perfil.SOLICITANTE)
            throw new NaoAutorizadoException("Usuario autenticado nao pode abrir chamados");
        Categoria categoria = categoriaRepository.findByIdAndAtivaTrue(request.categoriaId())
                .orElseThrow(() -> new DadosInvalidosException("Categoria indisponível. Escolha outra categoria.",
                        List.of(new FieldViolation("categoriaId", "Categoria indisponível. Escolha outra categoria."))));
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
        historico.save(HistoricoAtribuicao.registrarCriacao(salvo, solicitante, agora));
        return ChamadoResponse.from(salvo);
    }
}

package br.edu.impacta.resolveai.categoria;

import java.time.Clock;
import java.util.List;

import br.edu.impacta.resolveai.categoria.dto.CategoriaAdminResponse;
import br.edu.impacta.resolveai.categoria.dto.CategoriaRequest;
import br.edu.impacta.resolveai.categoria.dto.CategoriaResponse;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ConflitoException;
import br.edu.impacta.resolveai.shared.error.RecursoNaoEncontradoException;
import br.edu.impacta.resolveai.usuario.UsuarioService;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CategoriaService.class);
    private final CategoriaRepository categoriaRepository;
    private final UsuarioService usuarioService;
    private final Clock clock;

    public CategoriaService(CategoriaRepository categoriaRepository, UsuarioService usuarioService, Clock clock) {
        this.categoriaRepository = categoriaRepository;
        this.usuarioService = usuarioService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarAtivas() {
        return categoriaRepository.findAllByAtivaTrueOrderByNomeAsc().stream()
                .map(CategoriaResponse::from)
                .toList();
    }

    @Transactional
    public List<CategoriaAdminResponse> listarTodas(UsuarioPrincipal principal) {
        usuarioService.requireAdmin(principal);
        return categoriaRepository.findAllByOrderByNomeAscIdAsc().stream()
                .map(CategoriaAdminResponse::from)
                .toList();
    }

    @Transactional
    public CategoriaAdminResponse criar(UsuarioPrincipal principal, CategoriaRequest request) {
        usuarioService.requireAdmin(principal);
        var categoria = salvarComNomeUnico(Categoria.nova(request.nome(), request.descricao(), clock.instant()));
        LOGGER.info("AUDITORIA evento=CATEGORIA_CRIADA categoriaId={} administradorId={}", categoria.id(), principal.id());
        return CategoriaAdminResponse.from(categoria);
    }

    @Transactional
    public CategoriaAdminResponse editar(UsuarioPrincipal principal, Long id, CategoriaRequest request) {
        usuarioService.requireAdmin(principal);
        var categoria = buscarParaAtualizacao(id);
        categoria.alterarDados(request.nome(), request.descricao(), clock.instant());
        salvarComNomeUnico(categoria);
        LOGGER.info("AUDITORIA evento=CATEGORIA_EDITADA categoriaId={} administradorId={}", id, principal.id());
        return CategoriaAdminResponse.from(categoria);
    }

    @Transactional
    public CategoriaAdminResponse alterarSituacao(UsuarioPrincipal principal, Long id, boolean ativa) {
        usuarioService.requireAdmin(principal);
        var categoria = buscarParaAtualizacao(id);
        categoria.alterarSituacao(ativa, clock.instant());
        LOGGER.info("AUDITORIA evento=SITUACAO_CATEGORIA_ALTERADA categoriaId={} administradorId={} ativa={}",
                id, principal.id(), ativa);
        return CategoriaAdminResponse.from(categoria);
    }

    private Categoria buscarParaAtualizacao(Long id) {
        return categoriaRepository.findByIdParaAtualizacao(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
    }

    private Categoria salvarComNomeUnico(Categoria categoria) {
        try {
            return categoriaRepository.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException constraint
                    && "23505".equals(constraint.getSQLState())) {
                throw new ConflitoException("Já existe uma categoria com esse nome");
            }
            throw exception;
        }
    }
}

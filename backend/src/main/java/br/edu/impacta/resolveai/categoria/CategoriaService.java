package br.edu.impacta.resolveai.categoria;

import java.util.List;

import br.edu.impacta.resolveai.categoria.dto.CategoriaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarAtivas() {
        return categoriaRepository.findAllByAtivaTrueOrderByNomeAsc().stream()
                .map(CategoriaResponse::from)
                .toList();
    }
}

package br.edu.impacta.resolveai.chamado;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportacaoChamadosService {
    private static final int TAMANHO_LOTE = 100;
    private final ChamadoRepository chamados;
    private final EntityManager entityManager;
    private final int limite;

    public ExportacaoChamadosService(ChamadoRepository chamados, EntityManager entityManager,
            @Value("${app.chamados.exportacao.max-registros:10000}") int limite) {
        if (limite < 1 || limite > 100000)
            throw new IllegalArgumentException("Limite de exportação deve estar entre 1 e 100000");
        this.chamados = chamados;
        this.entityManager = entityManager;
        this.limite = limite;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public byte[] exportar(UsuarioPrincipal principal, FiltrosChamados filtros, Sort ordem) {
        var spec = VisibilidadeChamados.doUsuario(principal).and(filtros.specification());
        var pagina = chamados.findAll(spec, PageRequest.of(0, TAMANHO_LOTE, ordem));
        if (pagina.getTotalElements() > limite)
            throw new DadosInvalidosException("A exportação permite até " + limite
                    + " chamados. Refine os filtros e tente novamente.");
        // Mantém o CSV codificado em memória (com cópia na resposta), sem acumular entidades entre lotes.
        var csv = new ByteArrayOutputStream();
        csv.writeBytes(CsvChamados.CABECALHO.getBytes(StandardCharsets.UTF_8));
        while (true) {
            for (var chamado : pagina) {
                csv.writeBytes(CsvChamados.registro(chamado).getBytes(StandardCharsets.UTF_8));
            }
            entityManager.clear();
            if (!pagina.hasNext()) break;
            pagina = chamados.findAll(spec, pagina.nextPageable());
        }
        return csv.toByteArray();
    }
}

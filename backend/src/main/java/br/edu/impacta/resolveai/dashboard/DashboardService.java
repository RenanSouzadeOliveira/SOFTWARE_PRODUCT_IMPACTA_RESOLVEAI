package br.edu.impacta.resolveai.dashboard;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

import br.edu.impacta.resolveai.categoria.CategoriaService;
import br.edu.impacta.resolveai.chamado.PrioridadeChamado;
import br.edu.impacta.resolveai.chamado.StatusChamado;
import br.edu.impacta.resolveai.chamado.VisibilidadeChamados;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse.GrupoCategoria;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse.GrupoPrioridade;
import br.edu.impacta.resolveai.dashboard.dto.DashboardResumoResponse.GrupoStatus;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final DashboardRepository dashboardRepository;
    private final CategoriaService categoriaService;

    DashboardService(DashboardRepository dashboardRepository, CategoriaService categoriaService) {
        this.dashboardRepository = dashboardRepository;
        this.categoriaService = categoriaService;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResumoResponse resumir(UsuarioPrincipal principal) {
        var visibilidade = VisibilidadeChamados.doUsuario(principal);
        var status = dashboardRepository.contarPorEnum(visibilidade, "status", StatusChamado.class);
        var prioridades = dashboardRepository.contarPorEnum(visibilidade, "prioridade", PrioridadeChamado.class);

        var porStatus = Arrays.stream(StatusChamado.values())
                .map(valor -> new GrupoStatus(valor, status.getOrDefault(valor, 0L)))
                .toList();
        var porPrioridade = Arrays.stream(PrioridadeChamado.values())
                .map(valor -> new GrupoPrioridade(valor, prioridades.getOrDefault(valor, 0L)))
                .toList();

        var categorias = new HashMap<Long, GrupoCategoria>();
        dashboardRepository.contarPorCategoria(visibilidade)
                .forEach(grupo -> categorias.put(grupo.id(), grupo));
        categoriaService.listarAtivas().forEach(categoria -> categorias.putIfAbsent(
                categoria.id(), new GrupoCategoria(categoria.id(), categoria.nome(), 0)));
        var porCategoria = categorias.values().stream()
                .sorted(Comparator.comparing(GrupoCategoria::nome).thenComparing(GrupoCategoria::id))
                .toList();

        return new DashboardResumoResponse(
                porStatus.stream().mapToLong(GrupoStatus::quantidade).sum(),
                status.getOrDefault(StatusChamado.ABERTO, 0L),
                status.getOrDefault(StatusChamado.EM_ATENDIMENTO, 0L),
                status.getOrDefault(StatusChamado.RESOLVIDO, 0L) + status.getOrDefault(StatusChamado.FECHADO, 0L),
                porStatus, porPrioridade, porCategoria);
    }
}

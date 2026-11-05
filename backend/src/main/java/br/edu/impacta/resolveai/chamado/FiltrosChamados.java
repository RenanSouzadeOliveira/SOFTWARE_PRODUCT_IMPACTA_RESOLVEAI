package br.edu.impacta.resolveai.chamado;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.MultiValueMap;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;

/** Filtros AND; datas de criação em UTC, com fim exclusivo no dia seguinte. */
public record FiltrosChamados(String search, StatusChamado status, PrioridadeChamado prioridade,
        Long categoriaId, Long responsavel, LocalDate dataInicial, LocalDate dataFinal) {
    public static FiltrosChamados parse(MultiValueMap<String, String> params) {
        String search = unico(params, "search");
        LocalDate inicio = data(unico(params, "dataInicial"));
        LocalDate fim = data(unico(params, "dataFinal"));
        if (inicio != null && fim != null && inicio.isAfter(fim))
            throw new DadosInvalidosException("Data inicial deve ser anterior ou igual à data final");
        String categoria = unico(params, "categoria");
        String categoriaId = unico(params, "categoriaId");
        if (categoria != null && categoriaId != null && !categoria.equals(categoriaId))
            throw new DadosInvalidosException("Informe categoria ou categoriaId sem valores conflitantes");
        return new FiltrosChamados(search == null ? null : search.strip(),
                enumeration(unico(params, "status"), StatusChamado.class),
                enumeration(unico(params, "prioridade"), PrioridadeChamado.class),
                id(categoriaId == null ? categoria : categoriaId), id(unico(params, "responsavel")), inicio, fim);
    }

    public Specification<Chamado> specification() {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (search != null && !search.isBlank()) {
                String literal = search.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
                String pattern = "%" + literal + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("protocolo")), pattern, '!'),
                        cb.like(cb.lower(root.get("titulo")), pattern, '!'),
                        cb.like(cb.lower(root.get("descricao")), pattern, '!')));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (prioridade != null) predicates.add(cb.equal(root.get("prioridade"), prioridade));
            if (categoriaId != null) predicates.add(cb.equal(root.get("categoria").get("id"), categoriaId));
            if (responsavel != null) predicates.add(cb.equal(root.get("atendente").get("id"), responsavel));
            if (dataInicial != null) predicates.add(cb.greaterThanOrEqualTo(root.get("criadoEm"), dataInicial.atStartOfDay().toInstant(ZoneOffset.UTC)));
            if (dataFinal != null) predicates.add(cb.lessThan(root.get("criadoEm"), dataFinal.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static String unico(MultiValueMap<String, String> params, String name) {
        var values = params.get(name);
        if (values == null) return null;
        if (values.size() != 1) throw new DadosInvalidosException("Filtros não podem ser repetidos: " + name);
        return values.getFirst();
    }
    private static Long id(String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            long result = Long.parseLong(value);
            if (result < 1) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException ex) {
            throw new DadosInvalidosException("Identificadores de filtros devem ser inteiros positivos");
        }
    }
    private static <E extends Enum<E>> E enumeration(String value, Class<E> type) {
        if (value == null) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ex) { throw new DadosInvalidosException("Status ou prioridade inválidos"); }
    }
    private static LocalDate data(String value) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeException("formato");
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1) throw new DateTimeException("ano");
            return date;
        } catch (DateTimeException ex) { throw new DadosInvalidosException("Datas devem ser válidas no formato AAAA-MM-DD (UTC)"); }
    }
}

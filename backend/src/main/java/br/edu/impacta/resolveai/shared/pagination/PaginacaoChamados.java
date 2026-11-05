package br.edu.impacta.resolveai.shared.pagination;

import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;

/** Contrato comum das listagens: página zero-based e uma ordenação solicitada. */
public final class PaginacaoChamados {
    private static final Set<String> CAMPOS = Set.of("criadoEm", "atualizadoEm", "titulo", "status", "prioridade", "id");

    private PaginacaoChamados() { }

    public static PageRequest parse(MultiValueMap<String, String> parameters) {
        return parse(unico(parameters, "page"), unico(parameters, "size"), unico(parameters, "sort"));
    }

    private static String unico(MultiValueMap<String, String> parameters, String name) {
        var values = parameters.get(name);
        if (values == null) return null;
        if (values.size() != 1) throw new DadosInvalidosException("Parâmetros de paginação e ordenação não podem ser repetidos");
        return values.getFirst();
    }

    private static PageRequest parse(String page, String size, String sort) {
        int numero = inteiro(page, 0);
        int tamanho = inteiro(size, 20);
        if (numero < 0 || tamanho < 1 || tamanho > 100 || (long) numero * tamanho > Integer.MAX_VALUE) {
            throw new DadosInvalidosException("Página deve ser >= 0, tamanho entre 1 e 100 e deslocamento válido");
        }
        String[] partes = (sort == null ? "criadoEm,desc" : sort).split(",", -1);
        if (partes.length != 2 || !CAMPOS.contains(partes[0])
                || !(partes[1].equals("asc") || partes[1].equals("desc"))) {
            throw new DadosInvalidosException("Ordenação inválida; use campo,asc ou campo,desc. Campos: criadoEm, atualizadoEm, titulo, status, prioridade, id");
        }
        Sort ordem = Sort.by(new Sort.Order(Sort.Direction.fromString(partes[1]), partes[0]));
        if (!partes[0].equals("id")) ordem = ordem.and(Sort.by(Sort.Order.desc("id")));
        return PageRequest.of(numero, tamanho, ordem);
    }

    private static int inteiro(String valor, int padrao) {
        if (valor == null) return padrao;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(valor);
        } catch (NumberFormatException exception) {
            throw new DadosInvalidosException("Página e tamanho devem ser números inteiros válidos");
        }
    }
}

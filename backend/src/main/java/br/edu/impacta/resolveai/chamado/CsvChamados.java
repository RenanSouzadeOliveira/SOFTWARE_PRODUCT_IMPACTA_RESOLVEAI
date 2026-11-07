package br.edu.impacta.resolveai.chamado;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

/** CSV para planilhas: UTF-8, ponto e vírgula, aspas e CRLF. */
final class CsvChamados {
    static final String CABECALHO = "\uFEFF" + linha("Protocolo", "Título", "Status", "Prioridade",
            "Categoria", "Atendente responsável", "Data de criação (UTC)");

    private CsvChamados() { }

    static String registro(Chamado chamado) {
        return linha(chamado.protocolo(), chamado.titulo(), chamado.status().name(),
                chamado.prioridade().name(), chamado.categoria().nome(),
                chamado.atendente() == null ? "" : chamado.atendente().nome(),
                DateTimeFormatter.ISO_INSTANT.format(chamado.criadoEm()));
    }

    static String linha(String... values) {
        return Arrays.stream(values).map(CsvChamados::celula).collect(Collectors.joining(";")) + "\r\n";
    }

    static String celula(String value) {
        String texto = value == null ? "" : value;
        // Examine também controles, espaços Unicode e caracteres invisíveis antes da fórmula.
        int index = 0;
        boolean controleInicial = false;
        while (index < texto.length()) {
            int cp = texto.codePointAt(index);
            if (!(Character.isWhitespace(cp) || Character.isSpaceChar(cp)
                    || Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT)) break;
            controleInicial |= cp == '\t' || cp == '\r' || cp == '\n';
            index += Character.charCount(cp);
        }
        if (controleInicial || (index < texto.length()
                && "=+-@＝＋－＠".indexOf(texto.codePointAt(index)) >= 0)) {
            // Prefixo alfabético persistente: não depende do tratamento de apóstrofos do Excel/Calc.
            texto = "Texto: " + texto;
        }
        return "\"" + texto.replace("\"", "\"\"") + "\"";
    }
}

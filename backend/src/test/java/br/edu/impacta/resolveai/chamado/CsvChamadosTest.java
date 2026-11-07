package br.edu.impacta.resolveai.chamado;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class CsvChamadosTest {
    @Test void escapesCsvAndPreservesUnicode() {
        assertThat(CsvChamados.linha("Ação; \"rede\"\r\nsegunda linha", null))
                .isEqualTo("\"Ação; \"\"rede\"\"\r\nsegunda linha\";\"\"\r\n");
        assertThat(CsvChamados.CABECALHO).startsWith("\uFEFF\"Protocolo\"");
    }

    @Test void neutralizesFormulaPrefixesIncludingInvisibleCharacters() {
        for (String prefix : new String[]{"", " ", "\t", "\r", "\n", "\u0000", "\u001b", "\u0085",
                "\u00a0", "\u2003", "\uFEFF", "\u200B", " \t\u00a0\uFEFF"}) {
            for (String formula : new String[]{"=1+2", "+SUM(1)", "-1+2", "@SUM(1)",
                    "＝1+2", "＋1+2", "－1+2", "＠SUM(1)", "=1+2\";=3+4"}) {
                String value = prefix + formula;
                assertThat(CsvChamados.celula(value)).as("payload %s", value)
                        .isEqualTo("\"Texto: " + value.replace("\"", "\"\"") + "\"");
            }
        }
        for (String value : new String[]{"\ttexto", "\rtexto", "\ntexto"})
            assertThat(CsvChamados.celula(value)).startsWith("\"Texto: ");
        assertThat(CsvChamados.celula("Título comum = 2; não é fórmula")).isEqualTo("\"Título comum = 2; não é fórmula\"");
    }
}

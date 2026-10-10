package br.edu.impacta.resolveai.comentario;

import java.util.regex.Pattern;

/** Same edge whitespace as JavaScript String.trim, used by both request and domain. */
public final class TextoComentario {
    private static final Pattern ESPACOS_EXTERNOS = Pattern.compile(
            "^[\\p{Z}\\x{FEFF}\\x09-\\x0D]+|[\\p{Z}\\x{FEFF}\\x09-\\x0D]+$");
    private TextoComentario() { }
    public static String normalizar(String conteudo) {
        return conteudo == null ? null : ESPACOS_EXTERNOS.matcher(conteudo).replaceAll("");
    }
}

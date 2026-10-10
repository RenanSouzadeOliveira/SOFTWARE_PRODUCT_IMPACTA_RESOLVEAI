package br.edu.impacta.resolveai.comentario;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.hibernate.annotations.Immutable;

class ComentarioTest {
    @Test void shouldNormalizeBoundariesAndRejectInvalidContent() {
        Instant now = Instant.now();
        Usuario author = Usuario.novo("Pessoa", "comentario@example.com", "hash-ficticio", Perfil.SOLICITANTE, now);
        Chamado ticket = Chamado.abrir("RA-TESTE", "Titulo valido", "Descricao suficientemente longa", author,
                Categoria.nova("Categoria", null, now), now);
        for (int length : new int[] {1, 2000}) {
            Comentario c = Comentario.criar(ticket, author, " \t" + "x".repeat(length) + "\n ", now);
            assertThat(c.conteudo()).hasSize(length);
            assertThat(c.autor()).isSameAs(author);
            assertThat(c.criadoEm()).isEqualTo(now);
        }
        for (String invalid : new String[] {null, "", " \t\n ", "\u00A0", "\u2003", "\uFEFF", "x".repeat(2001)})
            assertThatThrownBy(() -> Comentario.criar(ticket, author, invalid, now))
                    .isInstanceOf(DadosInvalidosException.class);
    }

    @Test void shouldMatchJavascriptTrimForUnicodeEdgesInRequestAndDomain() {
        String whitespace = "\u00A0\u2003\uFEFF\u2028\u2029\t\n\u000B\f\r ";
        assertThat(TextoComentario.normalizar(whitespace)).isEmpty();
        assertThat(new br.edu.impacta.resolveai.comentario.dto.CriarComentarioRequest(whitespace + "texto" + whitespace).conteudo())
                .isEqualTo("texto");
        assertThat(TextoComentario.normalizar(whitespace + "texto\u00A0interno" + whitespace)).isEqualTo("texto\u00A0interno");
    }

    @Test void shouldExposeNoMutationMethodsAndBeImmutableToHibernate() {
        assertThat(Comentario.class.isAnnotationPresent(Immutable.class)).isTrue();
        assertThat(Comentario.class.getDeclaredMethods()).extracting(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("set") || name.startsWith("alterar") || name.startsWith("excluir"));
    }
}

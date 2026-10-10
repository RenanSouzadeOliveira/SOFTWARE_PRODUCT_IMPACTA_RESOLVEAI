package br.edu.impacta.resolveai.comentario;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import br.edu.impacta.resolveai.chamado.Chamado;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.FieldViolation;
import br.edu.impacta.resolveai.usuario.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Registro criado uma vez; conteudo e referencias nao possuem operacoes de edicao. */
@Entity @Immutable
@Table(name = "comentarios")
public class Comentario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id", nullable = false, updatable = false)
    private Chamado chamado;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false, updatable = false)
    private Usuario autor;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String conteudo;
    @Column(name = "criado_em", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant criadoEm;

    protected Comentario() { }

    public static Comentario criar(Chamado chamado, Usuario autor, String conteudo, Instant agora) {
        String texto = TextoComentario.normalizar(conteudo);
        if (texto == null || texto.isEmpty() || texto.length() > 2000)
            throw new DadosInvalidosException("Conteudo do comentario invalido", List.of(
                    new FieldViolation("conteudo", "deve conter entre 1 e 2000 caracteres apos remover espacos")));
        Comentario comentario = new Comentario();
        comentario.chamado = Objects.requireNonNull(chamado);
        comentario.autor = Objects.requireNonNull(autor);
        comentario.conteudo = texto;
        comentario.criadoEm = Objects.requireNonNull(agora);
        return comentario;
    }
    public Long id() { return id; }
    public Usuario autor() { return autor; }
    public String conteudo() { return conteudo; }
    public Instant criadoEm() { return criadoEm; }
}

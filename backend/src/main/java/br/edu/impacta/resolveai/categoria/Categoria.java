package br.edu.impacta.resolveai.categoria;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "categorias")
@Access(AccessType.FIELD)
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String nome;

    @Column(length = 255)
    private String descricao;

    @Column(nullable = false)
    private boolean ativa;

    @Column(name = "criado_em", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant atualizadoEm;

    @Version
    @Column(nullable = false)
    private long versao;

    protected Categoria() {
    }

    public static Categoria nova(String nome, String descricao, Instant agora) {
        Categoria categoria = new Categoria();
        categoria.nome = requiredText(nome, "nome", 100);
        categoria.descricao = optionalText(descricao, "descricao", 255);
        categoria.ativa = true;
        categoria.criadoEm = Objects.requireNonNull(agora, "agora nao pode ser nulo");
        categoria.atualizadoEm = agora;
        return categoria;
    }

    public void desativar(Instant agora) {
        this.ativa = false;
        this.atualizadoEm = Objects.requireNonNull(agora, "agora nao pode ser nulo");
    }

    public Long id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    public String descricao() {
        return descricao;
    }

    public boolean ativa() {
        return ativa;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }

    public long versao() {
        return versao;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " nao pode estar em branco");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " excede " + maxLength + " caracteres");
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " excede " + maxLength + " caracteres");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}

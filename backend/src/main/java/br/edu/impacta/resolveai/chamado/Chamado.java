package br.edu.impacta.resolveai.chamado;

import java.time.Instant;
import java.util.Objects;

import br.edu.impacta.resolveai.categoria.Categoria;
import br.edu.impacta.resolveai.usuario.Usuario;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "chamados")
@Access(AccessType.FIELD)
public class Chamado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, unique = true)
    private String protocolo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 2000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusChamado status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrioridadeChamado prioridade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private Usuario solicitante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendente_id")
    private Usuario atendente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(columnDefinition = "TEXT")
    private String solucao;

    @Column(name = "criado_em", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant atualizadoEm;

    @Column(name = "resolvido_em", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant resolvidoEm;

    @Column(name = "fechado_em", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant fechadoEm;

    @Version
    @Column(nullable = false)
    private long versao;

    protected Chamado() {
    }

    public static Chamado abrir(
            String protocolo,
            String titulo,
            String descricao,
            Usuario solicitante,
            Categoria categoria,
            Instant agora) {
        return abrir(protocolo, titulo, descricao, PrioridadeChamado.MEDIA, solicitante, categoria, agora);
    }

    public static Chamado abrir(
            String protocolo,
            String titulo,
            String descricao,
            PrioridadeChamado prioridade,
            Usuario solicitante,
            Categoria categoria,
            Instant agora) {
        Objects.requireNonNull(categoria, "categoria nao pode ser nula");
        if (!categoria.ativa()) {
            throw new IllegalStateException("Nao e permitido abrir chamado em categoria inativa");
        }

        Chamado chamado = new Chamado();
        chamado.protocolo = requiredText(protocolo, "protocolo", 30);
        chamado.titulo = requiredText(titulo, "titulo", 5, 120);
        chamado.descricao = requiredText(descricao, "descricao", 20, 2000);
        chamado.status = StatusChamado.ABERTO;
        chamado.prioridade = Objects.requireNonNull(prioridade, "prioridade nao pode ser nula");
        chamado.solicitante = Objects.requireNonNull(solicitante, "solicitante nao pode ser nulo");
        chamado.atendente = null;
        chamado.categoria = categoria;
        chamado.solucao = null;
        chamado.criadoEm = Objects.requireNonNull(agora, "agora nao pode ser nulo");
        chamado.atualizadoEm = agora;
        chamado.resolvidoEm = null;
        chamado.fechadoEm = null;
        return chamado;
    }

    public Long id() {
        return id;
    }

    public String protocolo() {
        return protocolo;
    }

    public String titulo() {
        return titulo;
    }

    public String descricao() {
        return descricao;
    }

    public StatusChamado status() {
        return status;
    }

    public PrioridadeChamado prioridade() {
        return prioridade;
    }

    public Usuario solicitante() {
        return solicitante;
    }

    public Usuario atendente() {
        return atendente;
    }

    public Categoria categoria() {
        return categoria;
    }

    public String solucao() {
        return solucao;
    }

    public Instant criadoEm() {
        return criadoEm;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }

    public Instant resolvidoEm() {
        return resolvidoEm;
    }

    public Instant fechadoEm() {
        return fechadoEm;
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

    private static String requiredText(String value, String field, int minLength, int maxLength) {
        String normalized = requiredText(value, field, maxLength);
        if (normalized.length() < minLength) {
            throw new IllegalArgumentException(field + " deve ter ao menos " + minLength + " caracteres");
        }
        return normalized;
    }
}

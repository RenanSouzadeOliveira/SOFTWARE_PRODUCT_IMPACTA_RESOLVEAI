package br.edu.impacta.resolveai.usuario;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "usuarios")
@Access(AccessType.FIELD)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant atualizadoEm;

    @Version
    @Column(nullable = false)
    private long versao;

    protected Usuario() {
    }

    public static Usuario novo(
            String nome,
            String email,
            String senhaHash,
            Perfil perfil,
            Instant agora) {
        Usuario usuario = new Usuario();
        usuario.nome = requiredText(nome, "nome", 120);
        usuario.email = requiredText(email, "email", 254).toLowerCase(Locale.ROOT);
        usuario.senhaHash = requiredText(senhaHash, "senhaHash", 100);
        usuario.perfil = Objects.requireNonNull(perfil, "perfil nao pode ser nulo");
        usuario.ativo = true;
        usuario.criadoEm = Objects.requireNonNull(agora, "agora nao pode ser nulo");
        usuario.atualizadoEm = agora;
        return usuario;
    }

    public void desativar(Instant agora) {
        this.ativo = false;
        this.atualizadoEm = Objects.requireNonNull(agora, "agora nao pode ser nulo");
    }

    public Long id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    public String email() {
        return email;
    }

    public Perfil perfil() {
        return perfil;
    }

    public boolean ativo() {
        return ativo;
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
}

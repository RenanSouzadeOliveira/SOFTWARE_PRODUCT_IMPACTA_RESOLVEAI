package br.edu.impacta.resolveai.chamado;

import java.time.Instant;
import br.edu.impacta.resolveai.usuario.Usuario;
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

@Entity
@Table(name = "historico_status")
public class HistoricoStatus {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id", nullable = false)
    private Chamado chamado;
    @Enumerated(EnumType.STRING) @Column(name = "status_anterior", nullable = false, length = 20)
    private StatusChamado statusAnterior;
    @Enumerated(EnumType.STRING) @Column(name = "status_novo", nullable = false, length = 20)
    private StatusChamado statusNovo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;
    @Column(name = "criado_em", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant criadoEm;
    @Column(length = 500)
    private String observacao;

    protected HistoricoStatus() { }

    public static HistoricoStatus registrar(Chamado chamado, StatusChamado anterior, Usuario autor,
            Instant agora, String observacao) {
        HistoricoStatus evento = new HistoricoStatus();
        evento.chamado = chamado;
        evento.statusAnterior = anterior;
        evento.statusNovo = chamado.status();
        evento.autor = autor;
        evento.criadoEm = agora;
        evento.observacao = observacao;
        return evento;
    }

    public Long id() { return id; }
    public StatusChamado statusAnterior() { return statusAnterior; }
    public StatusChamado statusNovo() { return statusNovo; }
    public Usuario autor() { return autor; }
    public Instant criadoEm() { return criadoEm; }
    public String observacao() { return observacao; }
}

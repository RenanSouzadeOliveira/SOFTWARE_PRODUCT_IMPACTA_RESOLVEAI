package br.edu.impacta.resolveai.atendimento;

import java.time.Instant;

import br.edu.impacta.resolveai.chamado.Chamado;
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

/** Evento imutavel escrito na mesma transacao da atribuicao. */
@Entity
@Table(name = "historico_chamados")
public class HistoricoAtribuicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id", nullable = false)
    private Chamado chamado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(name = "tipo_evento", nullable = false, length = 20)
    private String tipoEvento;

    @Column(name = "status_anterior", length = 20)
    private String statusAnterior;

    @Column(name = "status_novo", length = 20)
    private String statusNovo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendente_novo_id")
    private Usuario atendenteNovo;

    @Column(name = "criado_em", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant criadoEm;

    protected HistoricoAtribuicao() {
    }

    static HistoricoAtribuicao registrar(Chamado chamado, Usuario autor, Instant agora) {
        HistoricoAtribuicao evento = new HistoricoAtribuicao();
        evento.chamado = chamado;
        evento.autor = autor;
        evento.tipoEvento = "ATRIBUICAO";
        evento.statusAnterior = "ABERTO";
        evento.statusNovo = "EM_ATENDIMENTO";
        evento.atendenteNovo = autor;
        evento.criadoEm = agora;
        return evento;
    }
}

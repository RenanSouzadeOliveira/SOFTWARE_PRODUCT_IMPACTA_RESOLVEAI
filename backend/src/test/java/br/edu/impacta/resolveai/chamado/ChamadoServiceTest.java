package br.edu.impacta.resolveai.chamado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import br.edu.impacta.resolveai.chamado.dto.AbrirChamadoRequest;
import br.edu.impacta.resolveai.chamado.dto.ChamadoResponse;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.usuario.Perfil;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class ChamadoServiceTest {

    @Test
    void shouldRetryProtocolCollisionOutsideTheFailedTransaction() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ChamadoResponse expected = new ChamadoResponse(
                1L, "RA-protocolo-unico-0000", "Titulo", "Descricao suficientemente longa",
                StatusChamado.ABERTO, PrioridadeChamado.MEDIA, null, null, null, null,
                null, null, null, null);
        ChamadoCreationTransaction transaction = new ChamadoCreationTransaction(null, null, null, null) {
            @Override
            public ChamadoResponse criar(Long solicitanteId, AbrirChamadoRequest request, String protocolo) {
                if (attempts.getAndIncrement() == 0) {
                    throw new DataIntegrityViolationException("uk_chamados_protocolo");
                }
                return expected;
            }
        };
        ChamadoRepository repository = new ChamadoRepository() {
            @Override
            public Chamado save(Chamado chamado) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Chamado saveAndFlush(Chamado chamado) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<Chamado> findById(Long id) {
                return Optional.empty();
            }

            @Override
            public Optional<Chamado> findByProtocolo(String protocolo) {
                return Optional.of(new Chamado());
            }
        };
        ProtocoloChamadoGenerator generator = new ProtocoloChamadoGenerator() {
            @Override
            public String gerar() {
                return attempts.get() == 0 ? "RA-protocolo-colisao-000" : "RA-protocolo-unico-0000";
            }
        };
        ChamadoService service = new ChamadoService(transaction, repository, generator);

        ChamadoResponse response = service.abrir(
                new UsuarioPrincipal(1L, "solicitante@example.com", Perfil.SOLICITANTE),
                new AbrirChamadoRequest(
                        "Titulo", "Descricao suficientemente longa", 1L, PrioridadeChamado.MEDIA));

        assertThat(response).isSameAs(expected);
        assertThat(attempts).hasValue(2);
        Transactional transactional = ChamadoCreationTransaction.class
                .getMethod("criar", Long.class, AbrirChamadoRequest.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}

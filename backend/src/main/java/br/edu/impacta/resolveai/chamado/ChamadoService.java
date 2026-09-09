package br.edu.impacta.resolveai.chamado;

import java.util.List;

import br.edu.impacta.resolveai.chamado.dto.AbrirChamadoRequest;
import br.edu.impacta.resolveai.chamado.dto.ChamadoDetalheResponse;
import br.edu.impacta.resolveai.chamado.dto.ChamadoResponse;
import br.edu.impacta.resolveai.chamado.dto.ChamadoResumoResponse;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ConflitoException;
import br.edu.impacta.resolveai.shared.error.RecursoNaoEncontradoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChamadoService {

    private static final int MAX_PROTOCOL_ATTEMPTS = 3;

    private final ChamadoCreationTransaction creationTransaction;
    private final ChamadoRepository chamadoRepository;
    private final ProtocoloChamadoGenerator protocoloGenerator;

    public ChamadoService(
            ChamadoCreationTransaction creationTransaction,
            ChamadoRepository chamadoRepository,
            ProtocoloChamadoGenerator protocoloGenerator) {
        this.creationTransaction = creationTransaction;
        this.chamadoRepository = chamadoRepository;
        this.protocoloGenerator = protocoloGenerator;
    }

    public ChamadoResponse abrir(UsuarioPrincipal principal, AbrirChamadoRequest request) {
        for (int attempt = 1; attempt <= MAX_PROTOCOL_ATTEMPTS; attempt++) {
            String protocolo = protocoloGenerator.gerar();
            try {
                return creationTransaction.criar(principal.id(), request, protocolo);
            } catch (DataIntegrityViolationException exception) {
                if (chamadoRepository.findByProtocolo(protocolo).isEmpty()) {
                    throw exception;
                }
            }
        }
        throw new ConflitoException("Nao foi possivel gerar um protocolo unico; tente novamente");
    }

    @Transactional(readOnly = true)
    public List<ChamadoResumoResponse> listarMeus(UsuarioPrincipal principal) {
        return chamadoRepository.findAllBySolicitanteIdOrderByCriadoEmDesc(principal.id()).stream()
                .map(ChamadoResumoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChamadoDetalheResponse detalharMeu(Long chamadoId, UsuarioPrincipal principal) {
        return chamadoRepository.findByIdAndSolicitanteId(chamadoId, principal.id())
                .map(ChamadoDetalheResponse::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Chamado nao encontrado"));
    }
}

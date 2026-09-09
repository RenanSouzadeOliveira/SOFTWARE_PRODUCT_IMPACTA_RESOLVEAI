package br.edu.impacta.resolveai.chamado;

import br.edu.impacta.resolveai.chamado.dto.AbrirChamadoRequest;
import br.edu.impacta.resolveai.chamado.dto.ChamadoResponse;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.shared.error.ConflitoException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
}

package br.edu.impacta.resolveai.chamado;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class ProtocoloChamadoGenerator {

    private static final int RANDOM_BYTES = 16;
    private static final String PREFIX = "RA-";

    private final SecureRandom secureRandom;

    public ProtocoloChamadoGenerator() {
        this(new SecureRandom());
    }

    ProtocoloChamadoGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String gerar() {
        byte[] randomBytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}

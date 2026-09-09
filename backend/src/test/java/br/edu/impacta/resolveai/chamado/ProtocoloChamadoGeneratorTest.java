package br.edu.impacta.resolveai.chamado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ProtocoloChamadoGeneratorTest {

    @Test
    void shouldGenerateCompactSafeAndDistinctProtocols() {
        ProtocoloChamadoGenerator generator = new ProtocoloChamadoGenerator();
        Set<String> protocols = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            String protocol = generator.gerar();
            assertThat(protocol).hasSizeLessThanOrEqualTo(30).matches("^RA-[A-Za-z0-9_-]{22}$");
            protocols.add(protocol);
        }

        assertThat(protocols).hasSize(1_000);
    }
}

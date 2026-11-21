package br.edu.impacta.resolveai.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PerfilAtivoEnvironmentPostProcessorTest {
    @Test void deveExigirExatamenteUmPerfilSuportado() {
        for (String perfil : List.of("dev", "test", "prod")) {
            assertThatCode(() -> PerfilAtivoEnvironmentPostProcessor.validar(List.of(perfil))).doesNotThrowAnyException();
        }
        for (List<String> invalido : List.of(List.<String>of(), List.of("default"), List.of("producao"), List.of("dev", "prod"))) {
            assertThatThrownBy(() -> PerfilAtivoEnvironmentPostProcessor.validar(invalido)).as(invalido.toString())
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}

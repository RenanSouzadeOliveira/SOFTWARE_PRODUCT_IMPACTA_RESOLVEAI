package br.edu.impacta.resolveai.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class WebConfigTest {
    @Test void deveAceitarSomenteOrigensExplicitas() {
        assertThatCode(() -> new WebConfig(List.of("https://resolveai.example", " http://localhost:4200 "))).doesNotThrowAnyException();
        for (List<String> invalidas : List.of(List.<String>of(), List.of("*"), List.of("https://*.example"), List.of(""),
                List.of("resolveai.example"), List.of("https://resolveai.example/app"), List.of("null"))) {
            assertThatThrownBy(() -> new WebConfig(invalidas)).as(invalidas.toString()).isInstanceOf(IllegalStateException.class);
        }
    }
}

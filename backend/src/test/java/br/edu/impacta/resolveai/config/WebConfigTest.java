package br.edu.impacta.resolveai.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class WebConfigTest {
    @Test void deveAceitarSomenteOrigensExplicitas() {
        assertThatCode(() -> new WebConfig(List.of("https://resolveai.example", " http://localhost:4200 ",
                "http://127.0.0.1:4239", "http://[::1]:4200", "https://app.interno:8443"))).doesNotThrowAnyException();
        for (String invalida : List.of("*", "https://*.example", "", "resolveai.example", "https://resolveai.example/",
                "https://resolveai.example/app", "null", "https://usuario:senha@resolveai.example", "https://usuario@resolveai.example",
                "https://resolveai.example?x=1", "https://resolveai.example#frag", "ftp://resolveai.example", "file:///etc",
                "https://", "https://resolveai.example:porta", "https://resolve ai.example", "HTTPS://resolveai.example")) {
            assertThatThrownBy(() -> new WebConfig(List.of(invalida))).as(invalida).isInstanceOf(IllegalStateException.class);
        }
        for (List<String> invalidas : List.of(List.<String>of(), List.of("https://ok.example", "https://ok.example/x"))) {
            assertThatThrownBy(() -> new WebConfig(invalidas)).as(invalidas.toString()).isInstanceOf(IllegalStateException.class);
        }
    }
}

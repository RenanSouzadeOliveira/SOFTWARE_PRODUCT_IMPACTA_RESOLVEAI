package br.edu.impacta.resolveai.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class WebConfigTest {
    @Test void deveValidarPortasExplicitas() {
        for (String valida : List.of("https://app.example.test", "https://app.example.test:443", "http://app.example.test:8080",
                "https://app.example.test:65535", "http://localhost:1", "http://[::1]:4200", "https://[2001:db8::1]")) {
            assertThatCode(() -> new WebConfig(List.of(valida))).as(valida).doesNotThrowAnyException();
        }
        for (String invalida : List.of("https://app.example.test:0", "https://app.example.test:65536",
                "https://app.example.test:99999", "https://app.example.test:", "https://app.example.test:abc",
                "https://app.example.test:-1", "https://app.example.test:+80", "https://app.example.test:080",
                "http://[::1]:", "http://[::1]:70000")) {
            assertThatThrownBy(() -> new WebConfig(List.of(invalida))).as(invalida).isInstanceOf(IllegalStateException.class);
        }
    }

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

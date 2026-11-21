package br.edu.impacta.resolveai.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(new GlobalExceptionHandler(Clock.systemUTC()))
                .build();
    }

    @Test
    void shouldReturnConsistentValidationError() throws Exception {
        mockMvc.perform(post("/api/test-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Dados invalidos"))
                .andExpect(jsonPath("$.path").value("/api/test-validation"))
                .andExpect(jsonPath("$.violations[0].field").value("name"))
                .andExpect(jsonPath("$.violations[0].message").value("nao pode estar em branco"));
    }

    @Test
    void shouldHideUnexpectedErrorsAndPreserveFramework5xxEvenWithIncompatibleAccept() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/test-validation/falha")
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isInternalServerError())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Erro interno inesperado"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc:"))));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/test-validation/indisponivel")
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Servico temporariamente indisponivel"));
    }

    @RestController
    @RequestMapping("/api/test-validation")
    private static class ValidationController {

        @PostMapping
        void validate(@Valid @RequestBody ValidationRequest request) {
        }

        @org.springframework.web.bind.annotation.GetMapping("/falha")
        void falha() {
            throw new IllegalStateException("jdbc:postgresql://segredo-interno");
        }

        @org.springframework.web.bind.annotation.GetMapping("/indisponivel")
        void indisponivel() {
            throw new org.springframework.web.context.request.async.AsyncRequestTimeoutException();
        }
    }

    private record ValidationRequest(@NotBlank(message = "nao pode estar em branco") String name) {
    }
}

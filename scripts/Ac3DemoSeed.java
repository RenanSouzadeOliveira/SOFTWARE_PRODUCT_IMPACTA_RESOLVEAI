import br.edu.impacta.resolveai.ResolveAiApplication;
import br.edu.impacta.resolveai.atendimento.AtendimentoService;
import br.edu.impacta.resolveai.categoria.CategoriaRepository;
import br.edu.impacta.resolveai.chamado.*;
import br.edu.impacta.resolveai.chamado.dto.*;
import br.edu.impacta.resolveai.security.UsuarioPrincipal;
import br.edu.impacta.resolveai.usuario.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Processo acadêmico separado. Nenhuma alteração no Clock da aplicação normal. */
public class Ac3DemoSeed {
    static final String MARKER = "[AC3-ACADEMICO-V1]";
    static final SeedClock TIME = new SeedClock();
    static final String[] KEYS = {"solicitante1", "solicitante2", "solicitante3", "atendente1", "atendente2", "admin"};
    static final String[] SUBJECTS = {"Impressora não funciona", "Erro de acesso à rede", "Computador não inicializa",
            "Solicitação de instalação de software", "Problema de acesso ao sistema", "Falha de conexão com a internet",
            "Solicitação de manutenção", "Erro na autenticação"};
    static final ObjectMapper JSON = new ObjectMapper();

    @Configuration(proxyBeanMethods = false)
    public static class SeedConfiguration {
        @Bean @Primary Clock academicSeedClock() { return TIME; }
    }
    static final class SeedClock extends Clock {
        private volatile Instant now = Instant.parse("2026-01-01T09:00:00Z");
        void set(Instant value) { now = value; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    static Perfil role(String key) {
        return key.startsWith("solicitante") ? Perfil.SOLICITANTE : key.startsWith("atendente") ? Perfil.ATENDENTE : Perfil.ADMIN;
    }
    static String email(String key) { return "ac3-v1-" + key + "@example.invalid"; }
    static Instant date(int index) {
        return LocalDate.of(2026, 8 + index / 25, 5 + (index % 25) / 5 * 5)
                .atTime(10, 0).toInstant(ZoneOffset.UTC);
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
    public static void main(String[] args) throws Exception {
        require("dev".equals(System.getenv("SPRING_PROFILES_ACTIVE")), "Exige perfil dev.");
        Path credentials = Path.of(System.getenv("AC3_SEED_CREDENTIALS_FILE"));
        try (var context = new SpringApplicationBuilder(ResolveAiApplication.class, SeedConfiguration.class)
                .run("--spring.profiles.active=dev", "--server.port=0", "--server.address=127.0.0.1",
                        "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                        "--logging.level.br.edu.impacta.resolveai=ERROR");
             Connection lock = context.getBean(DataSource.class).getConnection()) {
            try (var statement = lock.createStatement(); var result = statement.executeQuery("SELECT pg_try_advisory_lock(20261106, 3)")) {
                result.next(); require(result.getBoolean(1), "Outro seed AC3 está em execução.");
            }
            var jdbc = context.getBean(JdbcTemplate.class);
            var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var users = context.getBean(UsuarioRepository.class);
            var encoder = context.getBean(PasswordEncoder.class);
            List<Long> categories = context.getBean(CategoriaRepository.class).findAllByAtivaTrueOrderByNomeAsc()
                    .stream().map(c -> c.id()).toList();
            require(categories.size() >= 3, "Exige três categorias ativas existentes.");

            com.fasterxml.jackson.databind.JsonNode state;
            if (Files.exists(credentials)) {
                require(Files.getPosixFilePermissions(credentials).equals(PosixFilePermissions.fromString("rw-------")),
                        "O arquivo de credenciais deve ter permissão 600.");
                state = JSON.readTree(Files.readString(credentials));
                require(MARKER.equals(state.path("marcador").asText()), "Arquivo de credenciais de outro conjunto.");
                categories = new ArrayList<>();
                for (var id : state.path("categorias")) categories.add(id.asLong());
                require(categories.size() == 3, "Categorias do manifesto inválidas.");
                for (Long id : categories) require(context.getBean(CategoriaRepository.class).findById(id).filter(c -> c.ativa()).isPresent(),
                        "Categoria do conjunto foi desativada ou removida; não alterei o conjunto.");
            } else {
                for (String key : KEYS) require(users.findByEmail(email(key)).isEmpty(),
                        "Já existe uma conta reservada sem seu arquivo de credenciais; não sobrescrevi a conta.");
                require(jdbc.queryForObject("SELECT count(*) FROM chamados WHERE titulo LIKE ?", Long.class, MARKER + " %") == 0,
                        "Há chamados do conjunto sem manifesto; não vou duplicá-los.");
                var root = JSON.createObjectNode(); root.put("marcador", MARKER);
                var cats = root.putArray("categorias"); categories.stream().limit(3).forEach(cats::add);
                var accounts = root.putObject("contas"); var random = new SecureRandom();
                for (String key : KEYS) {
                    byte[] bytes = new byte[24]; random.nextBytes(bytes);
                    var account = accounts.putObject(key);
                    account.put("email", email(key)); account.put("perfil", role(key).name());
                    account.put("senha", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
                }
                Files.createFile(credentials, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                Files.writeString(credentials, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root));
                state = root;
            }
            Map<String, UsuarioPrincipal> actors = new HashMap<>();
            for (String key : KEYS) {
                String password = state.path("contas").path(key).path("senha").asText();
                require(password.length() >= 8 && email(key).equals(state.path("contas").path(key).path("email").asText()),
                        "Conta inválida no arquivo privado.");
                Usuario user = users.findByEmail(email(key)).orElse(null);
                if (user == null) {
                    String hash = encoder.encode(password);
                    user = tx.execute(status -> users.save(Usuario.novo("AC3 V1 - " + key, email(key), hash, role(key), TIME.instant())));
                }
                require(user.ativo() && user.perfil() == role(key) && encoder.matches(password, user.senhaHashParaAutenticacao()),
                        "Conta reservada alterada ou credenciais divergentes; não alterei senha/perfil.");
                actors.put(key, new UsuarioPrincipal(user.id(), user.email(), user.perfil()));
            }
            var tickets = context.getBean(ChamadoService.class);
            var support = context.getBean(AtendimentoService.class);
            var cycle = context.getBean(CicloChamadoService.class);
            var validator = context.getBean(Validator.class);
            int inserted = 0, existing = 0;
            for (int i = 0; i < 75; i++) {
                String title = MARKER + " " + String.format("%03d", i + 1) + " - " + SUBJECTS[i % SUBJECTS.length];
                int ownerIndex = i < 45 ? 1 : i < 60 ? 2 : 3;
                var owner = actors.get("solicitante" + ownerIndex);
                var operator = actors.get("atendente" + (1 + i % 2));
                var found = jdbc.queryForList("SELECT id, solicitante_id FROM chamados WHERE titulo = ?", title);
                require(found.size() <= 1, "Marcador duplicado; interrompi sem apagar registros.");
                if (!found.isEmpty()) {
                    require(((Number) found.getFirst().get("solicitante_id")).longValue() == owner.id(), "Chamado com marcador pertence a outra conta.");
                    existing++; continue; // Nunca reescreve chamados já existentes, nem alterações feitas na gravação.
                }
                int stage = i % 5;
                PrioridadeChamado finalPriority = stage == 1 || stage == 2 ? PrioridadeChamado.ALTA
                        : stage == 4 ? PrioridadeChamado.BAIXA : PrioridadeChamado.MEDIA;
                PrioridadeChamado initial = stage == 0 ? finalPriority
                        : finalPriority == PrioridadeChamado.MEDIA ? PrioridadeChamado.BAIXA : PrioridadeChamado.MEDIA;
                Instant created = date(i); TIME.set(created);
                var request = new AbrirChamadoRequest(title,
                        "Dado fictício do laboratório acadêmico AC3. " + SUBJECTS[i % SUBJECTS.length]
                                + ". Ocorrência " + (i + 1) + " para demonstração de consulta, atendimento e auditoria.",
                        categories.get(i % 3), initial);
                require(validator.validate(request).isEmpty(), "Dados de abertura inválidos.");
                long id = tickets.abrir(owner, request).id();
                if (stage != 0) {
                    final int scenario = stage;
                    tx.executeWithoutResult(status -> {
                        TIME.set(created.plusSeconds(60)); support.assumir(id, operator);
                        TIME.set(created.plusSeconds(120)); cycle.alterarPrioridade(id, operator, new AlterarPrioridadeRequest(finalPriority));
                        if (scenario >= 2) {
                            TIME.set(created.plusSeconds(180)); cycle.alterarStatus(id, operator,
                                    new AlterarStatusRequest(StatusChamado.RESOLVIDO, "Correção acadêmica aplicada e funcionamento verificado."));
                        }
                        if (scenario == 2) {
                            TIME.set(created.plusSeconds(240)); cycle.alterarStatus(id, owner,
                                    new AlterarStatusRequest(StatusChamado.EM_ATENDIMENTO, "Ocorrência reapareceu na verificação acadêmica; solicito reabertura."));
                        } else if (scenario == 4) {
                            TIME.set(created.plusSeconds(240)); cycle.alterarStatus(id, owner,
                                    new AlterarStatusRequest(StatusChamado.FECHADO, null));
                        }
                    });
                }
                inserted++;
            }
            System.out.println("AC3_SEED_RESULT=" + JSON.writeValueAsString(Map.of("inseridos", inserted, "existentes", existing, "marcador", MARKER)));
            try (var statement = lock.createStatement()) { statement.execute("SELECT pg_advisory_unlock(20261106, 3)"); }
        }
    }
}

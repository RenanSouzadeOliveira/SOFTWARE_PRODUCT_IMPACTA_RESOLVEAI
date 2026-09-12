package br.edu.impacta.resolveai.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;

import br.edu.impacta.resolveai.auth.dto.LoginRequest;
import br.edu.impacta.resolveai.auth.dto.LoginResponse;
import br.edu.impacta.resolveai.auth.dto.RegisterRequest;
import br.edu.impacta.resolveai.security.JwtService;
import br.edu.impacta.resolveai.shared.error.ConflitoException;
import br.edu.impacta.resolveai.shared.error.DadosInvalidosException;
import br.edu.impacta.resolveai.shared.error.NaoAutorizadoException;
import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import br.edu.impacta.resolveai.usuario.UsuarioRepository;
import br.edu.impacta.resolveai.usuario.dto.UsuarioResponse;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final String INVALID_CREDENTIALS = "E-mail ou senha invalidos";
    private static final String DUMMY_PASSWORD = "credencial-ficticia-usada-apenas-para-tempo-constante";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final EntityManager entityManager;
    private final String dummyPasswordHash;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock,
            EntityManager entityManager) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.entityManager = entityManager;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Transactional
    public UsuarioResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        ensurePasswordFitsBcrypt(request.senha());
        if (usuarioRepository.findByEmail(email).isPresent()) {
            LOGGER.warn("AUDITORIA evento=CADASTRO_RECUSADO motivo=EMAIL_DUPLICADO");
            throw new ConflitoException("E-mail ja cadastrado");
        }

        Usuario usuario = Usuario.novo(
                request.nome(),
                email,
                passwordEncoder.encode(request.senha()),
                Perfil.SOLICITANTE,
                clock.instant());
        try {
            Usuario saved = usuarioRepository.save(usuario);
            entityManager.flush();
            LOGGER.info(
                    "AUDITORIA evento=USUARIO_CADASTRADO usuarioId={} perfil={}",
                    saved.id(),
                    saved.perfil());
            return UsuarioResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("AUDITORIA evento=CADASTRO_RECUSADO motivo=EMAIL_DUPLICADO");
            throw new ConflitoException("E-mail ja cadastrado");
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        if (request.senha().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new NaoAutorizadoException(INVALID_CREDENTIALS);
        }
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);
        String passwordHash = usuario == null
                ? dummyPasswordHash
                : usuario.senhaHashParaAutenticacao();
        boolean passwordMatches = passwordEncoder.matches(request.senha(), passwordHash);
        if (usuario == null || !usuario.ativo() || !passwordMatches) {
            LOGGER.warn("AUDITORIA evento=LOGIN_RECUSADO motivo=CREDENCIAIS_INVALIDAS");
            throw new NaoAutorizadoException(INVALID_CREDENTIALS);
        }

        String token = jwtService.generate(usuario);
        LOGGER.info(
                "AUDITORIA evento=LOGIN_REALIZADO usuarioId={} perfil={}",
                usuario.id(),
                usuario.perfil());
        return new LoginResponse(
                token,
                "Bearer",
                jwtService.expirationSeconds(),
                UsuarioResponse.from(usuario));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void ensurePasswordFitsBcrypt(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new DadosInvalidosException("Senha excede o limite seguro de 72 bytes do BCrypt");
        }
    }
}

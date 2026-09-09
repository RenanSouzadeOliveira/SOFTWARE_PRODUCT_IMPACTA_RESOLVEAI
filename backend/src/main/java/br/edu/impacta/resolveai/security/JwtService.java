package br.edu.impacta.resolveai.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import br.edu.impacta.resolveai.usuario.Perfil;
import br.edu.impacta.resolveai.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration expiration;
    private final String issuer;
    private final Clock clock;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration:PT2H}") Duration expiration,
            @Value("${app.jwt.issuer:resolveai-api}") String issuer,
            Clock clock) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT_SECRET deve ter no minimo 32 bytes");
        }
        if (expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("JWT_EXPIRATION deve ser uma duracao positiva");
        }
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("O issuer JWT nao pode estar em branco");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expiration = expiration;
        this.issuer = issuer;
        this.clock = clock;
    }

    public String generate(Usuario usuario) {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .subject(usuario.id().toString())
                .issuer(issuer)
                .claim("perfil", usuario.perfil().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public JwtIdentity parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Long usuarioId = Long.valueOf(claims.getSubject());
        String perfilClaim = claims.get("perfil", String.class);
        if (perfilClaim == null || perfilClaim.isBlank()) {
            throw new IllegalArgumentException("Token sem perfil");
        }
        Perfil perfil = Perfil.valueOf(perfilClaim);
        return new JwtIdentity(usuarioId, perfil);
    }

    public long expirationSeconds() {
        return expiration.toSeconds();
    }
}

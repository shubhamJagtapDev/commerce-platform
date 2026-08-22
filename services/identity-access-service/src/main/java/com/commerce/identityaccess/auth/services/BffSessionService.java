package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.auth.models.BffSessionAuthorityEntity;
import com.commerce.identityaccess.auth.models.BffSessionEntity;
import com.commerce.identityaccess.auth.models.PrincipalContext;
import com.commerce.identityaccess.auth.models.PrincipalKind;
import com.commerce.identityaccess.auth.repositories.BffSessionAuthorityRepository;
import com.commerce.identityaccess.auth.repositories.BffSessionRepository;
import jakarta.transaction.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
public class BffSessionService {
    private final BffSessionRepository sessionRepository;
    private final BffSessionAuthorityRepository authorityRepository;
    private final VersionedCryptoService cryptoService;
    private final AuthProperties properties;
    private final Clock clock;

    BffSessionService(
            BffSessionRepository sessionRepository,
            BffSessionAuthorityRepository authorityRepository,
            VersionedCryptoService cryptoService,
            AuthProperties properties,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.authorityRepository = authorityRepository;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String create(ValidatedOidcPrincipal principal, OidcTokenBundle tokenBundle) {
        Instant now = clock.instant();
        String rawHandle = cryptoService.randomUrlValue();
        String csrfToken = cryptoService.csrfToken(rawHandle);
        byte[] tokenBytes = serialize(tokenBundle);
        VersionedCryptoService.EncryptedValue encryptedTokens = cryptoService.encrypt("bff-token-bundle", tokenBytes);
        BffSessionEntity session = new BffSessionEntity(
                java.util.UUID.randomUUID(),
                cryptoService.hmac("bff-session-handle", rawHandle.getBytes(StandardCharsets.US_ASCII)),
                cryptoService.hmac("bff-csrf-verifier", csrfToken.getBytes(StandardCharsets.US_ASCII)),
                encryptedTokens.keyId(),
                encryptedTokens.ciphertext(),
                principal.kind(),
                principal.issuer(),
                principal.subject(),
                principal.oidcSessionId(),
                now,
                min(now.plus(properties.sessionIdleTtl()), now.plus(properties.sessionAbsoluteTtl())),
                now.plus(properties.sessionAbsoluteTtl()));
        sessionRepository.save(session);
        authorityRepository.saveAll(principal.authorities().stream()
                .map(authority -> new BffSessionAuthorityEntity(session.getSessionId(), authority))
                .toList());
        return rawHandle;
    }

    @Transactional
    public ResolvedSession resolve(String rawHandle) {
        Instant now = clock.instant();
        BffSessionEntity session = sessionRepository
                .findByHandleHash(
                        cryptoService.hmac("bff-session-handle", rawHandle.getBytes(StandardCharsets.US_ASCII)))
                .orElseThrow(MissingSessionException::new);
        if (!"ACTIVE".equals(session.getStatus())
                || !session.getIdleExpiresAt().isAfter(now)
                || !session.getAbsoluteExpiresAt().isAfter(now)) {
            throw new MissingSessionException();
        }
        String csrfToken = cryptoService.csrfToken(rawHandle);
        if (!java.security.MessageDigest.isEqual(
                session.getCsrfHash(),
                cryptoService.hmac("bff-csrf-verifier", csrfToken.getBytes(StandardCharsets.US_ASCII)))) {
            throw new MissingSessionException();
        }
        session.touch(now, min(now.plus(properties.sessionIdleTtl()), session.getAbsoluteExpiresAt()));
        Set<String> authorities = authorityRepository.findAllBySessionId(session.getSessionId()).stream()
                .map(BffSessionAuthorityEntity::getAuthorityCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new ResolvedSession(
                new PrincipalContext(
                        session.getSessionId(),
                        session.getIssuer(),
                        session.getSubject(),
                        PrincipalKind.valueOf(session.getPrincipalKind()),
                        session.getAuthenticatedAt(),
                        authorities),
                csrfToken);
    }

    private byte[] serialize(OidcTokenBundle tokenBundle) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(tokenBundle.idToken());
            output.writeUTF(tokenBundle.accessToken());
            output.writeBoolean(tokenBundle.refreshToken() != null);
            if (tokenBundle.refreshToken() != null) {
                output.writeUTF(tokenBundle.refreshToken());
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AuthenticationFailureException();
        }
    }

    private Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    public record ResolvedSession(PrincipalContext principal, String csrfToken) {}

    public record OidcTokenBundle(
            String idToken, String accessToken, @Nullable String refreshToken) {}

    public record ValidatedOidcPrincipal(
            String issuer,
            String subject,
            @Nullable String oidcSessionId,
            PrincipalKind kind,
            Set<String> authorities) {}
}

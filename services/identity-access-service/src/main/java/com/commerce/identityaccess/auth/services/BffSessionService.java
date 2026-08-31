package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.MissingSessionException;
import com.commerce.identityaccess.auth.models.BffSessionAuthorityEntity;
import com.commerce.identityaccess.auth.models.BffSessionEntity;
import com.commerce.identityaccess.auth.models.CreatedBffSession;
import com.commerce.identityaccess.auth.models.OidcTokenBundle;
import com.commerce.identityaccess.auth.models.PrincipalContext;
import com.commerce.identityaccess.auth.models.PrincipalKind;
import com.commerce.identityaccess.auth.models.ResolvedBffSession;
import com.commerce.identityaccess.auth.models.ValidatedOidcPrincipal;
import com.commerce.identityaccess.auth.repositories.BffSessionAuthorityRepository;
import com.commerce.identityaccess.auth.repositories.BffSessionRepository;
import com.commerce.identityaccess.customeraccount.models.CustomerAccount;
import com.commerce.identityaccess.customeraccount.services.CustomerAccountService;
import com.commerce.identityaccess.customeraccount.services.PrincipalAccessService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BffSessionService {
    private final BffSessionRepository sessionRepository;
    private final BffSessionAuthorityRepository authorityRepository;
    private final VersionedCryptoService cryptoService;
    private final TokenBundleCodec tokenBundleCodec;
    private final AuthProperties properties;
    private final CustomerAccountService customerAccountService;
    private final PrincipalAccessService principalAccessService;
    private final Clock clock;

    BffSessionService(
            BffSessionRepository sessionRepository,
            BffSessionAuthorityRepository authorityRepository,
            VersionedCryptoService cryptoService,
            TokenBundleCodec tokenBundleCodec,
            AuthProperties properties,
            CustomerAccountService customerAccountService,
            PrincipalAccessService principalAccessService,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.authorityRepository = authorityRepository;
        this.cryptoService = cryptoService;
        this.tokenBundleCodec = tokenBundleCodec;
        this.properties = properties;
        this.customerAccountService = customerAccountService;
        this.principalAccessService = principalAccessService;
        this.clock = clock;
    }

    @Transactional
    public CreatedBffSession create(ValidatedOidcPrincipal principal, OidcTokenBundle tokenBundle) {
        Instant now = clock.instant();
        CustomerAccount customerAccount = principal.kind() == PrincipalKind.CUSTOMER
                ? customerAccountService.establish(principal.issuer(), principal.subject())
                : null;
        UUID accountId = customerAccount == null ? null : customerAccount.accountId();
        Long securityEpoch = customerAccount == null ? null : customerAccount.securityEpoch();
        String rawHandle = cryptoService.randomUrlValue();
        String csrfToken = cryptoService.csrfToken(rawHandle);
        byte[] tokenBytes = tokenBundleCodec.encode(tokenBundle);
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
                accountId,
                securityEpoch,
                principal.oidcSessionId(),
                now,
                min(now.plus(properties.sessionIdleTtl()), now.plus(properties.sessionAbsoluteTtl())),
                now.plus(properties.sessionAbsoluteTtl()));
        sessionRepository.save(session);
        authorityRepository.saveAll(principal.authorities().stream()
                .map(authority -> new BffSessionAuthorityEntity(session.getSessionId(), authority))
                .toList());
        return new CreatedBffSession(
                rawHandle,
                new PrincipalContext(
                        session.getSessionId(),
                        principal.issuer(),
                        principal.subject(),
                        principal.kind(),
                        accountId,
                        securityEpoch,
                        now,
                        principal.authorities()));
    }

    @Transactional
    public ResolvedBffSession resolve(String rawHandle) {
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
        Set<String> authorities = authorityRepository.findAllBySessionId(session.getSessionId()).stream()
                .map(BffSessionAuthorityEntity::getAuthorityCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        PrincipalContext principal = new PrincipalContext(
                session.getSessionId(),
                session.getIssuer(),
                session.getSubject(),
                PrincipalKind.valueOf(session.getPrincipalKind()),
                session.getAccountId(),
                session.getSecurityEpoch(),
                session.getAuthenticatedAt(),
                authorities);
        if (principal.kind() == PrincipalKind.CUSTOMER) {
            principalAccessService.requireActiveCustomer(principal);
        }
        session.touch(now, min(now.plus(properties.sessionIdleTtl()), session.getAbsoluteExpiresAt()));
        return new ResolvedBffSession(principal, csrfToken);
    }

    @Transactional(readOnly = true)
    public String resolveMaintainerAccessToken(PrincipalContext principal) {
        Instant now = clock.instant();
        BffSessionEntity session =
                sessionRepository.findById(principal.sessionId()).orElseThrow(MissingSessionException::new);
        if (!"ACTIVE".equals(session.getStatus())
                || !PrincipalKind.CATALOG_MAINTAINER.name().equals(session.getPrincipalKind())
                || !session.getIssuer().equals(principal.issuer())
                || !session.getSubject().equals(principal.subject())
                || !session.getIdleExpiresAt().isAfter(now)
                || !session.getAbsoluteExpiresAt().isAfter(now)
                || !principal.authorities().contains("ROLE_CATALOG_MAINTAINER")) {
            throw new MissingSessionException();
        }
        byte[] encodedTokens = cryptoService.decrypt(
                "bff-token-bundle", session.getEncryptionKeyId(), session.getTokenBundleCiphertext());
        return tokenBundleCodec.decode(encodedTokens).accessToken();
    }

    private Instant min(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }
}

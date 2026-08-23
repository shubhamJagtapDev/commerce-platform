package com.commerce.identityaccess.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.models.OidcTokenBundle;
import com.commerce.identityaccess.auth.services.TokenBundleCodec;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TokenBundleCodecTest {
    private final TokenBundleCodec codec = new TokenBundleCodec();

    @Test
    void roundTripsTheProviderTokenBundle() {
        OidcTokenBundle tokens = new OidcTokenBundle("id-token", "access-token", "refresh-token");

        assertEquals(tokens, codec.decode(codec.encode(tokens)));
    }

    @Test
    void roundTripsABundleWithoutARefreshToken() {
        OidcTokenBundle tokens = new OidcTokenBundle("id-token", "access-token", null);

        assertEquals(tokens, codec.decode(codec.encode(tokens)));
    }

    @Test
    void rejectsMalformedStoredTokenMaterial() {
        byte[] encoded = codec.encode(new OidcTokenBundle("id-token", "access-token", null));
        byte[] malformed = Arrays.copyOf(encoded, encoded.length + 1);

        assertThrows(AuthenticationFailureException.class, () -> codec.decode(malformed));
    }
}

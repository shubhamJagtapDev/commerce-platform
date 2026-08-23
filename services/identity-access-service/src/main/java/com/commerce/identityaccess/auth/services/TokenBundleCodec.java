package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.exceptions.AuthenticationFailureException;
import com.commerce.identityaccess.auth.models.OidcTokenBundle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Encodes the encrypted token payload while hiding its durable binary representation. */
@Component
public final class TokenBundleCodec {
    public byte[] encode(OidcTokenBundle tokenBundle) {
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
            throw new AuthenticationFailureException("token_bundle_encoding_failed", exception);
        }
    }

    public OidcTokenBundle decode(byte[] encodedTokenBundle) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encodedTokenBundle))) {
            String idToken = input.readUTF();
            String accessToken = input.readUTF();
            @Nullable String refreshToken = input.readBoolean() ? input.readUTF() : null;
            if (input.available() != 0) {
                throw new AuthenticationFailureException("invalid_token_bundle");
            }
            return new OidcTokenBundle(idToken, accessToken, refreshToken);
        } catch (IOException exception) {
            throw new AuthenticationFailureException("invalid_token_bundle", exception);
        }
    }
}

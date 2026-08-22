package com.commerce.identityaccess.auth.services;

import com.commerce.identityaccess.auth.configs.AuthProperties;
import com.commerce.identityaccess.auth.exceptions.AuthCryptoException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public final class VersionedCryptoService {
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int HANDLE_BYTES = 32;

    private final AuthProperties properties;
    private final SecretKey encryptionKey;
    private final SecretKey hmacKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public VersionedCryptoService(AuthProperties properties) {
        this.properties = properties;
        encryptionKey = decodeKey(properties.crypto().encryptionKey(), "AES-256", 32, "AES");
        hmacKey = decodeKey(properties.crypto().hmacKey(), "HMAC-SHA-256", 32, "HmacSHA256");
    }

    public byte[] randomOpaqueValue() {
        byte[] value = new byte[HANDLE_BYTES];
        secureRandom.nextBytes(value);
        return value;
    }

    public String randomUrlValue() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomOpaqueValue());
    }

    public byte[] hmac(String purpose, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            mac.update(purpose.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) 0);
            return mac.doFinal(value);
        } catch (GeneralSecurityException exception) {
            throw new AuthCryptoException("Unable to calculate an authentication verifier", exception);
        }
    }

    public EncryptedValue encrypt(String purpose, byte[] plaintext) {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new EncryptedValue(
                    properties.crypto().encryptionKeyId(),
                    ByteBuffer.allocate(iv.length + ciphertext.length)
                            .put(iv)
                            .put(ciphertext)
                            .array());
        } catch (GeneralSecurityException exception) {
            throw new AuthCryptoException("Unable to protect authentication data", exception);
        }
    }

    public byte[] decrypt(String purpose, String keyId, byte[] payload) {
        if (!properties.crypto().encryptionKeyId().equals(keyId) || payload.length <= IV_BYTES) {
            throw new AuthCryptoException("Authentication data uses an unavailable encryption key");
        }
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(purpose.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new AuthCryptoException("Authentication data could not be verified", exception);
        }
    }

    public String csrfToken(String rawSessionHandle) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(hmac("bff-csrf", rawSessionHandle.getBytes(StandardCharsets.US_ASCII)));
    }

    private SecretKey decodeKey(String encoded, String label, int expectedLength, String algorithm) {
        try {
            byte[] key = Base64.getDecoder().decode(encoded);
            if (key.length != expectedLength) {
                throw new AuthCryptoException(label + " key must contain exactly " + expectedLength + " bytes");
            }
            return new SecretKeySpec(key, algorithm);
        } catch (IllegalArgumentException exception) {
            throw new AuthCryptoException(label + " key must be Base64 encoded", exception);
        }
    }

    public static final class EncryptedValue {
        private final String keyId;
        private final byte[] ciphertext;

        EncryptedValue(String keyId, byte[] ciphertext) {
            this.keyId = keyId;
            this.ciphertext = ciphertext.clone();
        }

        public String keyId() {
            return keyId;
        }

        public byte[] ciphertext() {
            return ciphertext.clone();
        }
    }
}

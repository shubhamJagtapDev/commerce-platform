package com.commerce.identityaccess.auth.models;

import org.jspecify.annotations.Nullable;

/** Provider tokens protected inside a durable BFF session and never returned to the browser. */
public record OidcTokenBundle(
        String idToken, String accessToken, @Nullable String refreshToken) {}

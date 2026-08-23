package com.commerce.identityaccess.auth.models;

/** The authoritative principal and CSRF token reconstructed from an active opaque session. */
public record ResolvedBffSession(PrincipalContext principal, String csrfToken) {}

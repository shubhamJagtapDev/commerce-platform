package com.commerce.identityaccess.auth.models;

/** Transactional authentication output and the stable handoff seam for future customer cart work. */
public record CreatedBffSession(String rawHandle, PrincipalContext principal) {}

package com.dryrun.brogres.security;

/**
 * Authenticated user identity carried in {@link org.springframework.security.core.Authentication}.
 */
public record BrogresPrincipal(long userId, String nick) {}

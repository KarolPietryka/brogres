package com.dryrun.brogres.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank @Size(max = 64) String nick,
            @NotBlank @Size(max = 512) String password) {}

    public record RegisterRequest(
            @NotBlank @Size(max = 64) String nick,
            @NotBlank @Size(max = 512) String password) {}

    public record AuthResponse(String token, String nick) {}
}

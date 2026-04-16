package com.dryrun.brogres.auth;

import com.dryrun.brogres.data.AppUser;
import com.dryrun.brogres.repo.AppUserRepository;
import com.dryrun.brogres.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String nick = request.nick().trim();
        AppUser user = appUserRepository
                .findByNickIgnoreCase(nick)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!user.getPasswordPlain().equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        String token = jwtService.createAccessToken(user.getId(), user.getNick());
        log.info("Login OK: userId={}, nick={}", user.getId(), user.getNick());
        return new AuthDtos.AuthResponse(token, user.getNick());
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String nick = request.nick().trim();
        if (nick.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nick is required");
        }
        if (appUserRepository.existsByNickIgnoreCase(nick)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nick already taken");
        }
        AppUser user = new AppUser();
        user.setNick(nick);
        user.setPasswordPlain(request.password());
        user = appUserRepository.save(user);
        String token = jwtService.createAccessToken(user.getId(), user.getNick());
        log.info("Register OK: userId={}, nick={}", user.getId(), user.getNick());
        return new AuthDtos.AuthResponse(token, user.getNick());
    }
}

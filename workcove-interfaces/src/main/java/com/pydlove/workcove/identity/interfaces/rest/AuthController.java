package com.pydlove.workcove.identity.interfaces.rest;

import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.application.command.LoginCommand;
import com.pydlove.workcove.identity.application.command.RegisterEmailCommand;
import com.pydlove.workcove.identity.application.query.AuthSessionGrant;
import com.pydlove.workcove.identity.application.query.CurrentAuthSession;
import com.pydlove.workcove.identity.interfaces.dto.AuthUserResponse;
import com.pydlove.workcove.identity.interfaces.dto.LoginRequest;
import com.pydlove.workcove.identity.interfaces.dto.RegisterRequest;
import com.pydlove.workcove.identity.interfaces.dto.SendEmailCodeRequest;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;
    private final String cookieName;
    private final String vaultCookieName;
    private final boolean secureCookie;

    public AuthController(
            AuthService authService,
            SessionCookieReader sessionCookieReader,
            @Value("${workcove.auth.cookie-name:workcove_session}") String cookieName,
            @Value("${workcove.vault.cookie-name:workcove_vault}")
            String vaultCookieName,
            @Value("${workcove.auth.cookie-secure:false}") boolean secureCookie) {
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
        this.cookieName = cookieName;
        this.vaultCookieName = vaultCookieName;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/email-codes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendEmailCode(@RequestBody SendEmailCodeRequest request) {
        authService.sendRegistrationCode(request.email());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserResponse register(@RequestBody RegisterRequest request) {
        return AuthUserResponse.from(authService.register(
                new RegisterEmailCommand(
                        request.email(),
                        request.emailCode(),
                        request.password(),
                        request.confirmPassword())));
    }

    @PostMapping("/login")
    public AuthUserResponse login(@RequestBody LoginRequest request,
                                  HttpServletResponse response) {
        AuthSessionGrant grant = authService.login(
                new LoginCommand(
                        request.email(),
                        request.password(),
                        request.rememberMe()));
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                sessionCookie(
                        grant.rawToken(),
                        grant.remembered()
                                ? Duration.between(Instant.now(), grant.expiresAt())
                                : null).toString());
        return AuthUserResponse.from(grant.user());
    }

    @GetMapping("/me")
    public AuthUserResponse me(HttpServletRequest request,
                               HttpServletResponse response) {
        String rawToken = sessionCookieReader.readToken(request);
        CurrentAuthSession current = authService.currentSession(rawToken);
        if (current.remembered()) {
            response.addHeader(
                    HttpHeaders.SET_COOKIE,
                    sessionCookie(
                            rawToken,
                            Duration.between(Instant.now(), current.expiresAt())).toString());
        }
        return AuthUserResponse.from(current.user());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(sessionCookieReader.readToken(request));
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                sessionCookie("", Duration.ZERO).toString());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(vaultCookieName, "")
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Strict")
                        .path("/api/vault")
                        .maxAge(Duration.ZERO)
                        .build()
                        .toString());
    }

    private ResponseCookie sessionCookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/");
        if (maxAge != null) builder.maxAge(maxAge);
        return builder.build();
    }
}

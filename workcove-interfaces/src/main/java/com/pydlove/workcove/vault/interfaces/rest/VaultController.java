package com.pydlove.workcove.vault.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pydlove.workcove.identity.application.command.AuthService;
import com.pydlove.workcove.identity.application.query.AuthenticatedUser;
import com.pydlove.workcove.identity.interfaces.security.SessionCookieReader;
import com.pydlove.workcove.shared.domain.DomainException;
import com.pydlove.workcove.vault.application.VaultService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private static final String ACTION = "manage-vault";

    private final VaultService vaultService;
    private final AuthService authService;
    private final SessionCookieReader sessionCookieReader;
    private final ObjectMapper objectMapper;
    private final String vaultCookieName;
    private final boolean secureCookie;
    private final String allowedOrigins;
    private final String rpName;

    public VaultController(
            VaultService vaultService,
            AuthService authService,
            SessionCookieReader sessionCookieReader,
            ObjectMapper objectMapper,
            @Value("${workcove.vault.cookie-name:workcove_vault}")
            String vaultCookieName,
            @Value("${workcove.vault.cookie-secure:false}")
            boolean secureCookie,
            @Value("${workcove.vault.allowed-origins:http://127.0.0.1:32581,http://localhost:32581}")
            String allowedOrigins,
            @Value("${workcove.vault.webauthn-rp-name:8小时工作台}")
            String rpName) {
        this.vaultService = vaultService;
        this.authService = authService;
        this.sessionCookieReader = sessionCookieReader;
        this.objectMapper = objectMapper;
        this.vaultCookieName = vaultCookieName;
        this.secureCookie = secureCookie;
        this.allowedOrigins = allowedOrigins;
        this.rpName = rpName;
    }

    @GetMapping("/status")
    public VaultStatusResponse status(HttpServletRequest request) {
        long ownerId = currentUser(request).id();
        VaultService.VaultStatus status = vaultService.status(
                ownerId,
                vaultToken(request));
        return new VaultStatusResponse(
                status.unlocked(),
                status.expiresAt(),
                status.passkeyEnabled());
    }

    @PostMapping("/unlock/password")
    public VaultUnlockResponse unlockWithPassword(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody PasswordUnlockRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        validateAction(action);
        VaultService.UnlockGrant grant = vaultService.unlockWithPassword(
                currentUser(request).id(),
                body.password());
        addVaultCookie(response, grant);
        return new VaultUnlockResponse(grant.expiresAt());
    }

    @PostMapping("/unlock/passkey/options")
    public PasskeyAuthenticationOptionsResponse passkeyAuthenticationOptions(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            HttpServletRequest request) {
        validateAction(action);
        WebAuthnOrigin origin = webAuthnOrigin(request);
        VaultService.AuthenticationOptions options =
                vaultService.passkeyAuthenticationOptions(
                        currentUser(request).id());
        return new PasskeyAuthenticationOptionsResponse(
                origin.rpId(),
                options.challenge(),
                options.credentialIds(),
                60_000,
                "required");
    }

    @PostMapping("/unlock/passkey")
    public VaultUnlockResponse unlockWithPasskey(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody PasskeyAssertionRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        validateAction(action);
        WebAuthnOrigin origin = webAuthnOrigin(request);
        VaultService.UnlockGrant grant =
                vaultService.finishPasskeyAuthentication(
                        currentUser(request).id(),
                        body.credentialId(),
                        responseJson(body.response()),
                        origin.origin(),
                        origin.rpId());
        addVaultCookie(response, grant);
        return new VaultUnlockResponse(grant.expiresAt());
    }

    @PostMapping("/lock")
    @ResponseStatus(NO_CONTENT)
    public void lock(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            HttpServletRequest request,
            HttpServletResponse response) {
        validateAction(action);
        vaultService.lock(currentUser(request).id(), vaultToken(request));
        clearVaultCookie(response);
    }

    @GetMapping("/entries")
    public List<VaultEntryResponse> entries(HttpServletRequest request) {
        return vaultService.entries(
                        currentUser(request).id(),
                        vaultToken(request))
                .stream()
                .map(VaultEntryResponse::from)
                .toList();
    }

    @GetMapping("/categories")
    public List<VaultCategoryResponse> categories(
            HttpServletRequest request) {
        return vaultService.categories(
                        currentUser(request).id(),
                        vaultToken(request))
                .stream()
                .map(VaultCategoryResponse::from)
                .toList();
    }

    @PostMapping("/categories")
    public VaultCategoryResponse createCategory(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody VaultCategoryRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return VaultCategoryResponse.from(vaultService.addCategory(
                currentUser(request).id(),
                vaultToken(request),
                body.name()));
    }

    @PutMapping("/categories/{id}")
    public VaultCategoryResponse renameCategory(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody VaultCategoryRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return VaultCategoryResponse.from(vaultService.renameCategory(
                currentUser(request).id(),
                vaultToken(request),
                id,
                body.name()));
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteCategory(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestParam long reassignTo,
            HttpServletRequest request) {
        validateAction(action);
        vaultService.deleteCategory(
                currentUser(request).id(),
                vaultToken(request),
                id,
                reassignTo);
    }

    @PostMapping("/entries")
    public VaultEntryResponse createEntry(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody VaultEntryRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return VaultEntryResponse.from(vaultService.saveEntry(
                currentUser(request).id(),
                vaultToken(request),
                null,
                body.toInput()));
    }

    @PutMapping("/entries/{id}")
    public VaultEntryResponse updateEntry(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            @RequestBody VaultEntryRequest body,
            HttpServletRequest request) {
        validateAction(action);
        return VaultEntryResponse.from(vaultService.saveEntry(
                currentUser(request).id(),
                vaultToken(request),
                id,
                body.toInput()));
    }

    @GetMapping("/entries/{id}/secret")
    public ResponseEntity<VaultSecretResponse> password(
            @PathVariable long id,
            HttpServletRequest request) {
        String password = vaultService.revealPassword(
                currentUser(request).id(),
                id,
                vaultToken(request));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new VaultSecretResponse(password));
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteEntry(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @PathVariable long id,
            HttpServletRequest request) {
        validateAction(action);
        vaultService.deleteEntry(
                currentUser(request).id(),
                id,
                vaultToken(request));
    }

    @PostMapping("/passkeys/registration/options")
    public PasskeyRegistrationOptionsResponse passkeyRegistrationOptions(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            HttpServletRequest request) {
        validateAction(action);
        AuthenticatedUser user = currentUser(request);
        WebAuthnOrigin origin = webAuthnOrigin(request);
        VaultService.RegistrationOptions options =
                vaultService.passkeyRegistrationOptions(
                        user.id(),
                        vaultToken(request));
        String userId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        Long.toString(user.id())
                                .getBytes(StandardCharsets.UTF_8));
        return new PasskeyRegistrationOptionsResponse(
                new RelyingParty(rpName, origin.rpId()),
                new WebAuthnUser(
                        userId,
                        user.email(),
                        user.displayName()),
                options.challenge(),
                60_000,
                List.of(
                        Map.of("type", "public-key", "alg", -7),
                        Map.of("type", "public-key", "alg", -257)),
                "none",
                new AuthenticatorSelection(
                        "platform",
                        "required",
                        true,
                        "required"),
                options.credentialIds().stream()
                        .map((id) -> Map.of(
                                "type", "public-key",
                                "id", id))
                        .toList());
    }

    @PostMapping("/passkeys/registration")
    @ResponseStatus(NO_CONTENT)
    public void finishPasskeyRegistration(
            @RequestHeader(value = "X-WorkCove-Action", required = false)
            String action,
            @RequestBody RegistrationResponseRequest body,
            HttpServletRequest request) {
        validateAction(action);
        WebAuthnOrigin origin = webAuthnOrigin(request);
        vaultService.finishPasskeyRegistration(
                currentUser(request).id(),
                vaultToken(request),
                responseJson(body.response()),
                origin.origin(),
                origin.rpId());
    }

    private AuthenticatedUser currentUser(HttpServletRequest request) {
        return authService.currentSession(
                sessionCookieReader.readToken(request)).user();
    }

    private String vaultToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter((cookie) -> vaultCookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void addVaultCookie(
            HttpServletResponse response,
            VaultService.UnlockGrant grant) {
        Duration maxAge = Duration.between(Instant.now(), grant.expiresAt());
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                vaultCookie(grant.rawToken(), maxAge).toString());
    }

    private void clearVaultCookie(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                vaultCookie("", Duration.ZERO).toString());
    }

    private ResponseCookie vaultCookie(String value, Duration maxAge) {
        return ResponseCookie.from(vaultCookieName, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/api/vault")
                .maxAge(maxAge)
                .build();
    }

    private WebAuthnOrigin webAuthnOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        boolean allowed = origin != null
                && Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .anyMatch(origin::equals);
        if (!allowed) {
            throw new DomainException("WebAuthn 来源不受信任");
        }
        try {
            String rpId = URI.create(origin).getHost();
            if (rpId == null || rpId.isBlank()) {
                throw new DomainException("WebAuthn 来源无效");
            }
            return new WebAuthnOrigin(origin, rpId);
        } catch (IllegalArgumentException exception) {
            throw new DomainException("WebAuthn 来源无效");
        }
    }

    private String responseJson(JsonNode response) {
        if (response == null || response.isNull()) {
            throw new DomainException("WebAuthn 响应不能为空");
        }
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new DomainException("WebAuthn 响应格式无效");
        }
    }

    private void validateAction(String action) {
        if (!ACTION.equals(action)) {
            throw new DomainException("不支持的密码库操作");
        }
    }

    private record WebAuthnOrigin(String origin, String rpId) {
    }

    public record PasswordUnlockRequest(String password) {
    }

    public record PasskeyAssertionRequest(
            String credentialId,
            JsonNode response) {
    }

    public record RegistrationResponseRequest(JsonNode response) {
    }

    public record VaultUnlockResponse(Instant expiresAt) {
    }

    public record VaultCategoryRequest(String name) {
    }

    public record VaultCategoryResponse(
            long id,
            String name,
            int sortOrder) {

        private static VaultCategoryResponse from(
                VaultService.VaultCategoryView category) {
            return new VaultCategoryResponse(
                    category.id(),
                    category.name(),
                    category.sortOrder());
        }
    }

    public record VaultStatusResponse(
            boolean unlocked,
            Instant expiresAt,
            boolean passkeyEnabled) {
    }

    public record VaultEntryRequest(
            String name,
            String username,
            String password,
            String url,
            String category,
            String notes) {

        private VaultService.VaultEntryInput toInput() {
            return new VaultService.VaultEntryInput(
                    name,
                    username,
                    password,
                    url,
                    category,
                    notes);
        }
    }

    public record VaultEntryResponse(
            long id,
            String name,
            String username,
            String url,
            String category,
            String notes,
            Instant updatedAt) {

        private static VaultEntryResponse from(
                VaultService.VaultEntryView entry) {
            return new VaultEntryResponse(
                    entry.id(),
                    entry.name(),
                    entry.username(),
                    entry.url(),
                    entry.category(),
                    entry.notes(),
                    entry.updatedAt());
        }
    }

    public record VaultSecretResponse(String password) {
    }

    public record PasskeyAuthenticationOptionsResponse(
            String rpId,
            String challenge,
            List<String> allowCredentials,
            int timeout,
            String userVerification) {
    }

    public record RelyingParty(String name, String id) {
    }

    public record WebAuthnUser(
            String id,
            String name,
            String displayName) {
    }

    public record AuthenticatorSelection(
            String authenticatorAttachment,
            String residentKey,
            boolean requireResidentKey,
            String userVerification) {
    }

    public record PasskeyRegistrationOptionsResponse(
            RelyingParty rp,
            WebAuthnUser user,
            String challenge,
            int timeout,
            List<Map<String, Object>> pubKeyCredParams,
            String attestation,
            AuthenticatorSelection authenticatorSelection,
            List<Map<String, String>> excludeCredentials) {
    }
}

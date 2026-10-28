package com.pydlove.workcove.identity.interfaces.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieReader {

    private final String cookieName;

    public SessionCookieReader(
            @Value("${workcove.auth.cookie-name:workcove_session}") String cookieName) {
        this.cookieName = cookieName;
    }

    public String readToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            if (!token.isEmpty()) return token;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}

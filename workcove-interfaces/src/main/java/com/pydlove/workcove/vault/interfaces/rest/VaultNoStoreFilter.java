package com.pydlove.workcove.vault.interfaces.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class VaultNoStoreFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/vault/")) {
            response.setHeader(
                    HttpHeaders.CACHE_CONTROL,
                    "no-store, no-cache, must-revalidate");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        }
        filterChain.doFilter(request, response);
    }
}

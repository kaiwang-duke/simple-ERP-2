package com.nineforce.config;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        logger.debug("Request path: {}", path);
        if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/") ||
                path.startsWith("/static/") || path.endsWith(".html")) {
            logger.debug("Bypassing Firebase authentication for path: {}", path);
            filterChain.doFilter(request, response);
            return;
        } else {
            logger.debug("Not bypassing, applying Firebase authentication for path: {}", path);
        }

        String token = request.getHeader("Authorization");
        logger.debug("Received token: {}", token);
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            logger.debug("Token after stripping 'Bearer ': {}", token);
            try {
                FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token);
                logger.debug("Decoded Firebase token: {}", decodedToken);
                if (decodedToken != null) {
                    Authentication auth = new UsernamePasswordAuthenticationToken(decodedToken.getUid(), null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    logger.debug("Authentication set for user: {}", decodedToken.getUid());
                }
            } catch (FirebaseAuthException e) {
                logger.error("FirebaseAuthException: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        logger.debug("FirebasAuthenticationFilter: doFilterInternal called");
        filterChain.doFilter(request, response);
    }
}

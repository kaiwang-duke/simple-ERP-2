package com.nineforce.util;

import com.google.firebase.database.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class FirebaseAuthUtil {

    private static final String FIREBASE_AUTH_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=AIzaSyA4rWpOW536oW2KHykh0umtMuKKAMIcWHg";


    public String authenticateUser(String email, String password) throws InvalidCredentialsException {
        RestTemplate restTemplate = new RestTemplate();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("email", email);
        requestBody.put("password", password);
        requestBody.put("returnSecureToken", true); // boolean is fine

        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    FIREBASE_AUTH_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody),
                    new ParameterizedTypeReference<>() {}
            );

            // 1) check HTTP status
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new InvalidCredentialsException("Authentication failed (HTTP " + resp.getStatusCode().value() + ")");
            }

            // 2) check body non-null
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                throw new InvalidCredentialsException("Authentication failed (empty response)");
            }

            // 3) extract idToken safely
            Object tokenObj = body.get("idToken");
            if (tokenObj instanceof String token && !token.isBlank()) {
                return token;
            }

            // Optional: bubble up Firebase error message if present
            Object errObj = body.get("error");
            String message = "Authentication failed for user: " + email;
            if (errObj instanceof Map<?, ?> errMap) {
                Object msg = errMap.get("message");
                if (msg instanceof String s && !s.isBlank()) {
                    message = s;
                }
            }
            throw new InvalidCredentialsException(message);

        } catch (org.springframework.web.client.RestClientException ex) {
            // network/serialization/etc.
            throw new InvalidCredentialsException("Authentication request failed: " + ex.getMessage(), ex);
        }
    }

    @Nullable
    public String getUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }

        Object p = auth.getPrincipal();

        // If your filter stores the Google FirebaseToken as principal
        if (p instanceof com.google.firebase.auth.FirebaseToken ft) {
            if (ft.getEmail() != null && !ft.getEmail().isBlank()) return ft.getEmail();
            if (ft.getName() != null && !ft.getName().isBlank()) return ft.getName();
            return ft.getUid(); // last-resort identifier
        }

        // If Spring Security stored a UserDetails
        if (p instanceof org.springframework.security.core.userdetails.UserDetails ud) {
            return ud.getUsername();
        }

        // If it’s just a String principal (common: "anonymousUser")
        if (p instanceof String s) {
            return "anonymousUser".equalsIgnoreCase(s) ? null : s;
        }

        return null;
    }
}


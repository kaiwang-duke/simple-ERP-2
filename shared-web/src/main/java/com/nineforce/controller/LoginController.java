package com.nineforce.controller;

import com.nineforce.util.FirebaseAuthUtil;
import com.nineforce.config.FirebaseAuthenticationToken;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.nineforce.util.InvalidCredentialsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Profile({"cloud","local"})
@Controller
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    private final FirebaseAuthUtil firebaseAuthUtil;
    private final FirebaseAuth firebaseAuth;

    // Constructor Injection
    public LoginController(FirebaseAuthUtil firebaseAuthUtil, FirebaseAuth firebaseAuth) {
        this.firebaseAuthUtil = firebaseAuthUtil;
        this.firebaseAuth = firebaseAuth;
    }

    @GetMapping("/login")
    public String login() {
        logger.debug("GET /login called");
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam("email") String email,
                        @RequestParam("password") String password,
                        Model model,
                        HttpServletRequest request) {
        logger.warn("POST /login called with email: {} ", email);
        try {
            String idToken = firebaseAuthUtil.authenticateUser(email, password);
            logger.debug("ID token received: {}", idToken);
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            logger.debug("Token verified: {}", decodedToken.getUid());
            Authentication authentication = new FirebaseAuthenticationToken(decodedToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            logger.debug("Authentication successful for email: {}", email);

            // Manually store the security context in the session
            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());

            return "redirect:/index";
        } catch (InvalidCredentialsException e) { // Changed from FirebaseAuthException to InvalidCredentialsException
            logger.error("Firebase authentication failed for email: {}. {}", email, e.getMessage());
            model.addAttribute("error", "Invalid email or password");
            return "login";
        } catch (Exception e) {
            logger.error("Unexpected error during authentication", e);
            model.addAttribute("error", "An unexpected error occurred. Please try again.");
            return "login";
        }
    }
}

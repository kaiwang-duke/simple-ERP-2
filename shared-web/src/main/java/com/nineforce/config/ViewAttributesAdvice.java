package com.nineforce.config;

import com.nineforce.util.FirebaseAuthUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.lang.Nullable;

import java.security.Principal;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;  //scope to MVC controllers only

@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor

public class ViewAttributesAdvice {

    private final FirebaseAuthUtil firebaseAuthUtil;

    @ModelAttribute("_csrf")
    public CsrfToken csrf(@Nullable CsrfToken token) {
        return token;
    }

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated(Authentication auth) {
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    //TODO use real user names, not just email addresses
    @ModelAttribute("userName")
    public String userName(Principal principal) {
        return principal != null ? principal.getName() : null;
    }


    @ModelAttribute("userEmail")
    public String userEmail() {
        return firebaseAuthUtil.getUserEmail(); // null when anonymous
    }

    // Optional: gives IntelliJ and runtime a default so 'title' exists everywhere
    @ModelAttribute("title")
    public String defaultTitle() {
        return "ERP System";
    }
}
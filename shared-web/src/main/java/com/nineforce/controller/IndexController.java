package com.nineforce.controller;

import com.nineforce.util.FirebaseAuthUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    @Autowired
    private FirebaseAuthUtil firebaseAuthUtil;

    @GetMapping("/index")
    public String index(Model model) {
        String userEmail = firebaseAuthUtil.getUserEmail();
        //model.addAttribute("userEmail", userEmail);
        model.addAttribute("title", "Management Dashboard 0.1");
        return "index";
    }
}





package com.veici.asset.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());

        model.addAttribute("username", authentication.getName());
        model.addAttribute("employee", authorities.contains("ROLE_EMPLOYEE"));
        model.addAttribute("approver", authorities.contains("ROLE_DEPT_APPROVER"));
        model.addAttribute("assetAdmin", authorities.contains("ROLE_ASSET_ADMIN"));
        model.addAttribute("systemAdmin", authorities.contains("ROLE_SYSTEM_ADMIN"));

        boolean hasNavigation = authorities.stream().anyMatch(Set.of(
                "ROLE_EMPLOYEE",
                "ROLE_DEPT_APPROVER",
                "ROLE_ASSET_ADMIN",
                "ROLE_SYSTEM_ADMIN"
        )::contains);

        model.addAttribute("hasNavigation", hasNavigation);
        return "index";
    }
}
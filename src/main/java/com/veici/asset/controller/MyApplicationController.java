package com.veici.asset.controller;

import com.veici.asset.service.AssetApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class MyApplicationController {

    private final AssetApplicationService applicationService;

    @GetMapping("/employee/applications")
    public String list(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute(
                "applicationPage",
                applicationService.findMyApplications(page)
        );

        return "employee/applications";
    }
}
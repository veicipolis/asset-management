package com.veici.asset.controller;

import com.veici.asset.repository.AssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AssetController {

    private final AssetRepository assetRepository;

    @GetMapping("/assets")
    public String list(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        var pageable = PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by("assetCode").ascending()
        );

        model.addAttribute("assetPage", assetRepository.findAll(pageable));
        return "assets/list";
    }
}
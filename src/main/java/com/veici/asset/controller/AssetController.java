package com.veici.asset.controller;

import com.veici.asset.entity.AssetStatus;
import com.veici.asset.repository.AssetCategoryRepository;
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
    private final AssetCategoryRepository categoryRepository;

    @GetMapping("/assets")
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String assetCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) AssetStatus status,
            Model model) {

        assetCode = normalize(assetCode);
        name = normalize(name);

        var pageable = PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by("assetCode").ascending()
        );

        model.addAttribute("assetPage", assetRepository.search(
                assetCode, name, categoryId, status, pageable));

        // 为下一步的筛选栏准备选项和已填写的条件。
        model.addAttribute("categories",
                categoryRepository.findAll(Sort.by("name")));
        model.addAttribute("statuses", AssetStatus.values());
        model.addAttribute("assetCode", assetCode);
        model.addAttribute("name", name);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("status", status);

        return "assets/list";
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
package com.veici.asset.controller;

import com.veici.asset.dto.AvailableAssetView;
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
public class EmployeeAssetController {

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository categoryRepository;

    @GetMapping("/employee/assets")
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String assetCode,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            Model model) {

        assetCode = normalize(assetCode);
        name = normalize(name);

        var pageable = PageRequest.of(
                Math.max(page, 0),
                20,
                Sort.by("assetCode").ascending()
        );

        // 状态由服务端固定，不能通过网址参数查询其他状态。
        var assetPage = assetRepository.search(
                assetCode, name, categoryId, AssetStatus.IDLE, pageable
        ).map(asset -> new AvailableAssetView(
                asset.getId(),
                asset.getAssetCode(),
                asset.getName(),
                asset.getCategory().getName(),
                asset.getLocation()
        ));

        model.addAttribute("assetPage", assetPage);
        model.addAttribute("categories",
                categoryRepository.findAll(Sort.by("name")));
        model.addAttribute("assetCode", assetCode);
        model.addAttribute("name", name);
        model.addAttribute("categoryId", categoryId);

        return "employee/assets";
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
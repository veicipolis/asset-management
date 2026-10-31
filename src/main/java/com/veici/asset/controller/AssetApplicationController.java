package com.veici.asset.controller;

import com.veici.asset.dto.AssetApplicationForm;
import com.veici.asset.dto.AvailableAssetView;
import com.veici.asset.repository.AssetRepository;
import com.veici.asset.service.AssetApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/employee/assets")
public class AssetApplicationController {

    private final AssetRepository assetRepository;
    private final AssetApplicationService applicationService;

    @GetMapping("/{id}/apply")
    public String form(@PathVariable("id") Long id, Model model) {
        model.addAttribute("form", new AssetApplicationForm());
        return prepareForm(id, model);
    }

    @PostMapping("/{id}/apply")
    public String submit(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("form") AssetApplicationForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return prepareForm(id, model);
        }

        try {
            applicationService.submit(id, form);
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("application.invalid", exception.getMessage());
            return prepareForm(id, model);
        } catch (DataIntegrityViolationException exception) {
            bindingResult.reject(
                    "application.conflict",
                    "提交失败：可能存在重复申请或关联数据已变化，请刷新后检查"
            );
            return prepareForm(id, model);
        }

        redirectAttributes.addFlashAttribute(
                "successMessage", "领用申请已提交，等待部门审批");
        return "redirect:/employee/assets";
    }

    private String prepareForm(Long id, Model model) {
        var asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "资产不存在"));

        // 页面仅接收员工可见字段，不传递完整资产实体。
        model.addAttribute("asset", new AvailableAssetView(
                asset.getId(),
                asset.getAssetCode(),
                asset.getName(),
                null,
                asset.getLocation()
        ));
        return "employee/apply";
    }
}
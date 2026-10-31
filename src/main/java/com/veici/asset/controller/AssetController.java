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
import com.veici.asset.dto.AssetCreateForm;
import com.veici.asset.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.veici.asset.dto.AssetEditForm;
import com.veici.asset.exception.AssetEditConflictException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class AssetController {

    private final AssetRepository assetRepository;
    private final AssetCategoryRepository categoryRepository;
    private final AssetService assetService;

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

    @GetMapping("/assets/new")
    public String createForm(Model model) {
        model.addAttribute("form", new AssetCreateForm());
        return prepareCreateForm(model);
    }

    @PostMapping("/assets")
    public String create(
            @Valid @ModelAttribute("form") AssetCreateForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return prepareCreateForm(model);
        }

        try {
            assetService.create(form);
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("asset.create", exception.getMessage());
            return prepareCreateForm(model);
        } catch (DataIntegrityViolationException exception) {
            bindingResult.reject(
                    "asset.conflict",
                    "保存失败：资产编号可能重复，或关联数据已变化，请检查后重试"
            );
            return prepareCreateForm(model);
        }

        redirectAttributes.addFlashAttribute("successMessage", "资产新增成功");
        return "redirect:/assets";
    }

    private String prepareCreateForm(Model model) {
        model.addAttribute("categories",
                categoryRepository.findAll(Sort.by("name")));
        return "assets/create";
    }

    @GetMapping("/assets/{id}/edit")
    public String editForm(@PathVariable("id") Long id, Model model) {
        model.addAttribute("form", assetService.getEditForm(id));
        return prepareEditForm(id, model);
    }

    @PostMapping("/assets/{id}/edit")
    public String update(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("form") AssetEditForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return prepareEditForm(id, model);
        }

        try {
            assetService.update(id, form);
        } catch (AssetEditConflictException
                 | OptimisticLockingFailureException exception) {
            bindingResult.reject(
                    "asset.conflict",
                    "资产已被其他操作修改，请点击“重新加载最新数据”后再编辑"
            );
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("asset.invalid", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            bindingResult.reject(
                    "asset.integrity",
                    "保存失败，关联数据可能已变化，请重新加载后重试"
            );
        }

        if (bindingResult.hasErrors()) {
            return prepareEditForm(id, model);
        }

        redirectAttributes.addFlashAttribute("successMessage", "资产修改成功");
        return "redirect:/assets";
    }

    private String prepareEditForm(Long id, Model model) {
        model.addAttribute("assetId", id);
        model.addAttribute("categories",
                categoryRepository.findAll(Sort.by("name")));
        return "assets/edit";
    }
}
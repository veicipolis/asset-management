package com.veici.asset.controller;

import com.veici.asset.dto.AssetCategoryForm;
import com.veici.asset.service.AssetCategoryAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/system/categories")
public class AssetCategoryAdminController {

    private final AssetCategoryAdminService categoryService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute("categoryPage", categoryService.findCategories(page));
        return "system/categories";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new AssetCategoryForm());
        model.addAttribute("categoryId", null);
        return "system/category-form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") AssetCategoryForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (!bindingResult.hasErrors()) {
            try {
                categoryService.create(form);
                redirectAttributes.addFlashAttribute("success", "资产类别已创建");
                return "redirect:/system/categories";
            } catch (IllegalArgumentException ex) {
                bindingResult.reject("saveFailed", ex.getMessage());
            } catch (DataIntegrityViolationException ex) {
                bindingResult.reject("saveFailed", "保存失败，类别名称可能已存在");
            }
        }

        model.addAttribute("categoryId", null);
        return "system/category-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", categoryService.getEditForm(id));
        model.addAttribute("categoryId", id);
        return "system/category-form";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") AssetCategoryForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (!bindingResult.hasErrors()) {
            try {
                categoryService.update(id, form);
                redirectAttributes.addFlashAttribute("success", "类别名称已更新");
                return "redirect:/system/categories";
            } catch (IllegalArgumentException ex) {
                bindingResult.reject("saveFailed", ex.getMessage());
            } catch (OptimisticLockingFailureException ex) {
                bindingResult.reject(
                        "saveFailed", "类别信息已发生变化，请重新打开编辑页面");
            } catch (DataIntegrityViolationException ex) {
                bindingResult.reject("saveFailed", "保存失败，类别名称可能已存在");
            }
        }

        model.addAttribute("categoryId", id);
        return "system/category-form";
    }
}
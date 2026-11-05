package com.veici.asset.controller;

import com.veici.asset.dto.DepartmentForm;
import com.veici.asset.service.DepartmentAdminService;
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
@RequestMapping("/system/departments")
public class DepartmentAdminController {

    private final DepartmentAdminService departmentService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute(
                "departmentPage", departmentService.findDepartments(page));
        return "system/departments";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new DepartmentForm());
        model.addAttribute("departmentId", null);
        return "system/department-form";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") DepartmentForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (!bindingResult.hasErrors()) {
            try {
                departmentService.create(form);
                redirectAttributes.addFlashAttribute("success", "部门已创建");
                return "redirect:/system/departments";
            } catch (IllegalArgumentException ex) {
                bindingResult.reject("saveFailed", ex.getMessage());
            } catch (DataIntegrityViolationException ex) {
                bindingResult.reject("saveFailed", "保存失败，部门名称可能已存在");
            }
        }

        model.addAttribute("departmentId", null);
        return "system/department-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", departmentService.getEditForm(id));
        model.addAttribute("departmentId", id);
        return "system/department-form";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("form") DepartmentForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (!bindingResult.hasErrors()) {
            try {
                departmentService.update(id, form);
                redirectAttributes.addFlashAttribute("success", "部门名称已更新");
                return "redirect:/system/departments";
            } catch (IllegalArgumentException ex) {
                bindingResult.reject("saveFailed", ex.getMessage());
            } catch (OptimisticLockingFailureException ex) {
                bindingResult.reject(
                        "saveFailed", "部门信息已发生变化，请重新打开编辑页面");
            } catch (DataIntegrityViolationException ex) {
                bindingResult.reject("saveFailed", "保存失败，部门名称可能已存在");
            }
        }

        model.addAttribute("departmentId", id);
        return "system/department-form";
    }
}
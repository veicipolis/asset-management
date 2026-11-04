package com.veici.asset.controller;

import com.veici.asset.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.veici.asset.dto.UserEnabledForm;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.veici.asset.dto.UserCreateForm;
import org.springframework.dao.DataIntegrityViolationException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/system/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute("userPage", userAdminService.findUsers(page));
        return "system/users";
    }

    @PostMapping("/{id}/enabled")
    public String changeEnabled(
            @PathVariable Long id,
            @Valid @ModelAttribute UserEnabledForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (id <= 0 || bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "error", "提交参数无效，请刷新后重试");
            return "redirect:/system/users";
        }

        try {
            userAdminService.changeEnabled(id, form);
            redirectAttributes.addFlashAttribute(
                    "success", Boolean.TRUE.equals(form.getEnabled())
                            ? "用户已启用" : "用户已停用");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            redirectAttributes.addFlashAttribute(
                    "error", "用户信息已发生变化，请刷新后重试");
        }

        return "redirect:/system/users";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new UserCreateForm());
        model.addAttribute("departments", userAdminService.findDepartments());
        return "system/user-create";
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") UserCreateForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (!bindingResult.hasErrors()) {
            try {
                userAdminService.create(form);
                redirectAttributes.addFlashAttribute(
                        "success", "用户创建成功，请按需要分配角色");
                return "redirect:/system/users";
            } catch (IllegalArgumentException ex) {
                bindingResult.reject("createFailed", ex.getMessage());
            } catch (DataIntegrityViolationException ex) {
                bindingResult.reject(
                        "createFailed",
                        "创建失败，用户名可能已存在或部门信息发生变化，请检查后重试"
                );
            }
        }

        // 校验失败后要求重新输入密码。
        form.setPassword(null);
        form.setConfirmPassword(null);

        model.addAttribute("departments", userAdminService.findDepartments());
        return "system/user-create";
    }
}
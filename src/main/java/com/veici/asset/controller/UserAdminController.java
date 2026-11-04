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
}
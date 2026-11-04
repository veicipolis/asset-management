package com.veici.asset.controller;

import com.veici.asset.service.ReturnReceiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/returns")
public class ReturnReceiveController {

    private final ReturnReceiveService returnReceiveService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute(
                "returnPage", returnReceiveService.findPending(page));
        return "admin/returns";
    }

    @PostMapping("/{id}/receive")
    public String receive(
            @PathVariable Long id,
            @RequestParam Long version,
            RedirectAttributes redirectAttributes) {

        if (id <= 0 || version < 0) {
            redirectAttributes.addFlashAttribute("error", "提交参数无效");
            return "redirect:/admin/returns";
        }

        try {
            returnReceiveService.receive(id, version);
            redirectAttributes.addFlashAttribute(
                    "success", "归还接收成功，资产已恢复为闲置");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (OptimisticLockingFailureException
                 | DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute(
                    "error", "记录已发生变化，请刷新后重试");
        }

        return "redirect:/admin/returns";
    }
}
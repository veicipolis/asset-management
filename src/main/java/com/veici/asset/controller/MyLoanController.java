package com.veici.asset.controller;

import com.veici.asset.service.ReturnApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.veici.asset.dto.ReturnCancelForm;
import com.veici.asset.service.ReturnCancelService;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.validation.BindingResult;

@Controller
@RequiredArgsConstructor
@RequestMapping("/employee/loans")
public class MyLoanController {

    private final ReturnApplicationService returnApplicationService;
    private final ReturnCancelService returnCancelService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute(
                "loanPage", returnApplicationService.findMyLoans(page));
        return "employee/loans";
    }

    @PostMapping("/{id}/return")
    public String requestReturn(
            @PathVariable Long id,
            RedirectAttributes redirectAttributes) {

        if (id <= 0) {
            redirectAttributes.addFlashAttribute("error", "领用记录编号无效");
            return "redirect:/employee/loans";
        }

        try {
            returnApplicationService.submit(id);
            redirectAttributes.addFlashAttribute(
                    "success", "归还申请已提交，请等待资产管理员接收");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute(
                    "error", "提交失败，记录可能已发生变化，请刷新后重试");
        }

        return "redirect:/employee/loans";
    }

    @PostMapping("/returns/{id}/cancel")
    public String cancelReturn(
            @PathVariable Long id,
            @Valid @ModelAttribute ReturnCancelForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (id <= 0 || bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "error", "提交参数无效，请刷新后重试");
            return "redirect:/employee/loans";
        }

        try {
            returnCancelService.cancel(id, form);
            redirectAttributes.addFlashAttribute(
                    "success", "归还申请已取消，资产仍由你领用");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            redirectAttributes.addFlashAttribute(
                    "error", "归还申请已发生变化，请刷新后重试");
        }

        return "redirect:/employee/loans";
    }
}
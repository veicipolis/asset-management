package com.veici.asset.controller;

import com.veici.asset.service.ReturnApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/employee/loans")
public class MyLoanController {

    private final ReturnApplicationService returnApplicationService;

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
}
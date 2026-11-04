package com.veici.asset.controller;

import com.veici.asset.dto.ApplicationApprovalForm;
import com.veici.asset.service.ApplicationApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/approver/applications")
public class ApplicationApprovalController {

    private final ApplicationApprovalService approvalService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute("applicationPage", approvalService.findPending(page));
        return "approver/applications";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        var item = approvalService.getForReview(id);

        var form = new ApplicationApprovalForm();
        form.setVersion(item.version());

        model.addAttribute("item", item);
        model.addAttribute("form", form);
        return "approver/review";
    }

    @PostMapping("/{id}")
    public String review(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("form") ApplicationApprovalForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("item", approvalService.getForReview(id));
            return "approver/review";
        }

        try {
            approvalService.review(id, form);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", exception.getMessage());
            return "redirect:/approver/applications";
        } catch (OptimisticLockingFailureException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "申请已发生变化，请重新打开审批页面");
            return "redirect:/approver/applications";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage", "审批结果已保存");
        return "redirect:/approver/applications";
    }
}
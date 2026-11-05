package com.veici.asset.controller;

import com.veici.asset.dto.ApplicationCancelForm;
import com.veici.asset.service.ApplicationCancelService;
import com.veici.asset.service.AssetApplicationService;
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
public class MyApplicationController {

    private final AssetApplicationService applicationService;
    private final ApplicationCancelService cancelService;

    @GetMapping("/employee/applications")
    public String list(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute(
                "applicationPage",
                applicationService.findMyApplications(page)
        );

        return "employee/applications";
    }

    @PostMapping("/employee/applications/{id}/cancel")
    public String cancel(
            @PathVariable Long id,
            @Valid @ModelAttribute ApplicationCancelForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (id <= 0 || bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "error", "提交参数无效，请刷新后重试");
            return "redirect:/employee/applications";
        }

        try {
            cancelService.cancel(id, form);
            redirectAttributes.addFlashAttribute("success", "领用申请已取消");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            redirectAttributes.addFlashAttribute(
                    "error", "申请已发生变化，请刷新后重试");
        }

        return "redirect:/employee/applications";
    }
}
package com.veici.asset.controller;

import com.veici.asset.dto.AssetIssueForm;
import com.veici.asset.service.AssetIssueService;
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
@RequestMapping("/admin/issues")
public class AssetIssueController {

    private final AssetIssueService issueService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute("issuePage", issueService.findApproved(page));
        return "admin/issues";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        var item = issueService.getForIssue(id);

        var form = new AssetIssueForm();
        form.setApplicationVersion(item.applicationVersion());
        form.setAssetVersion(item.assetVersion());

        model.addAttribute("item", item);
        model.addAttribute("form", form);
        return "admin/issue.html";
    }

    @PostMapping("/{id}")
    public String issue(
            @PathVariable("id") Long id,
            @Valid @ModelAttribute("form") AssetIssueForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "发放信息不完整，请重新打开确认页面");
            return "redirect:/admin/issues";
        }

        try {
            issueService.issue(id, form);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", exception.getMessage());
            return "redirect:/admin/issues";
        } catch (OptimisticLockingFailureException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "申请或资产已发生变化，请重新确认");
            return "redirect:/admin/issues";
        } catch (DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "发放失败：可能已被发放或关联数据发生变化，请刷新后检查"
            );
            return "redirect:/admin/issues";
        }

        redirectAttributes.addFlashAttribute(
                "successMessage", "资产发放成功，已生成领用记录");
        return "redirect:/admin/issues";
    }
}
package com.veici.asset.controller;

import com.veici.asset.dto.OperationLogView;
import com.veici.asset.service.AdminOperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
@RequestMapping("/system/logs")
public class AdminOperationLogController {

    private final AdminOperationLogService logService;

    @GetMapping
    public String list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("action", action);
        model.addAttribute("targetType", targetType);
        model.addAttribute("targetId", targetId);

        try {
            model.addAttribute("logPage", logService.search(
                    startDate, endDate, action, targetType, targetId, page));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("logPage", Page.<OperationLogView>empty());
        }

        return "system/logs";
    }
}
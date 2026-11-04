package com.veici.asset.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationApprovalForm {

    @NotNull(message = "缺少版本号，请刷新页面")
    @PositiveOrZero(message = "版本号不合法")
    private Long version;

    @NotNull(message = "请选择审批结果")
    private ApprovalDecision decision;

    @Size(max = 200, message = "审批意见不能超过200个字符")
    private String comment;

    @AssertTrue(message = "驳回申请时必须填写原因")
    public boolean isRejectionReasonValid() {
        return decision != ApprovalDecision.REJECT
                || (comment != null && !comment.isBlank());
    }
}
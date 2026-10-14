package com.paytech.client.contract.service.processor.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** O02 申请与分类读取、O03 设置与 O04 确认共用的内部请求；只有 O03 携带分类。 */
@Getter
@AllArgsConstructor
public class ClientClassificationRequest {
    /** Risk 主审核任务 ID。 */
    @Positive
    private final long caseId;
    /** 操作者 Account ID；读取时不记录。 */
    @Positive
    private final long actorId;
    /** O03 分类，1—20 项；读取与确认为空列表。 */
    @Size(max = 20)
    private final List<@Valid Item> items;

    /** 一条分类。 */
    @Getter
    @AllArgsConstructor
    public static class Item {
        /** 是否主要分类。 */
        private final boolean primary;
        /** 四位 MCC。 */
        @NotBlank
        @Pattern(regexp = "^[0-9]{4}$")
        private final String mccCode;
        /** 行业代码。 */
        @NotBlank
        private final String industryCode;
    }
}

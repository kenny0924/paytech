package com.paytech.client.master.service.processor.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 整体替换 Local Partner 角色的内部请求。 */
@Getter
@AllArgsConstructor
public class ReplacePartnerRolesRequest {
    /** Local Partner ID。 */
    @Positive
    private final long localPartnerId;
    /** 替换后的全部角色 Code；非空、不重复与取值由 validate 校验。 */
    @NotNull
    private final List<String> roles;
    /** 调用方读取时的根修订号。 */
    @Positive
    private final long expectedChangeVersion;
    /** 修改原因，1—500 字符。 */
    @NotBlank
    @Size(max = 500)
    private final String reason;
    /** 操作者 Account ID。 */
    @Positive
    private final long actorId;
}

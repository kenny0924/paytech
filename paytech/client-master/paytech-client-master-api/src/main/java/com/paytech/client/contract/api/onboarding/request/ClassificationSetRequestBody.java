package com.paytech.client.contract.api.onboarding.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import lombok.Data;

/** O03 整体替换的分类集合；确认人和时间由服务端生成。 */
@Data
public class ClassificationSetRequestBody {
    /** 分类，1—20 项。 */
    @NotEmpty
    @Size(max = 20)
    private List<@Valid ClassificationItemBody> items;
}

package com.paytech.platform.support.dataquery.service.processor.request;

import java.util.List;

import com.paytech.platform.support.dataquery.domain.ReorderQueryKeyTypesCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 整体重排键类型的内部请求。 */
@Getter
@AllArgsConstructor
public class ReorderQueryKeyTypesRequest {

    /** 可信操作人账号。 */
    @NotBlank
    private final String actor;

    /** 可信会话标识。 */
    @NotBlank
    private final String session;

    /** 全部键类型按新顺序排列及各自原版本。 */
    @NotEmpty
    private final List<ReorderQueryKeyTypesCommand.Item> items;
}

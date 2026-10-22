package com.paytech.channel.connector.protocol.service.processor.request;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.channel.connector.api.enums.ScriptExecutionStage;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道下级配置（原子 API、脚本）列表查询的已规范化输入。 */
@Getter
@AllArgsConstructor
public class ListIntegrationChildrenRequest {

    /** 路径中的渠道编码。 */
    private final String integrationCode;

    /** 已校验的分页参数，每页最多 100 条。 */
    private final PagingRequestBody paging;

    /** LIKE 包含模式，通配符已转为文字；null 表示不筛选。 */
    private final String keyword;

    /** 脚本执行阶段筛选；原子 API 列表与不筛选时为 null。 */
    private final ScriptExecutionStage stage;
}

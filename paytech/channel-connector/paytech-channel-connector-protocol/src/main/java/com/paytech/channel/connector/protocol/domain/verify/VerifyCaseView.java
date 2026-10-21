package com.paytech.channel.connector.protocol.domain.verify;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.api.enums.VerifyCaseStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 验证用例详情或列表项，含最近一次运行与验证状态。 */
@Getter
@AllArgsConstructor
public class VerifyCaseView {

    /** 用例编码。 */
    private final String caseCode;

    /** 名称。 */
    private final String caseName;

    /** 说明。 */
    private final String description;

    /** 验证账号编码。 */
    private final String accountCode;

    /** 步骤。 */
    private final JsonNode steps;

    /** 步骤涉及的标准 API，按步骤顺序去重。 */
    private final List<String> standardApiCodes;

    /** 验证状态。 */
    private final VerifyCaseStatus verifyStatus;

    /** 最近一次运行；没有为空。 */
    private final VerifyRunBriefView latestRun;

    /** 版本。 */
    private final int version;

    /** 创建时间。 */
    private final Instant createTime;

    /** 最后修改时间。 */
    private final Instant updateTime;

    /** 最后修改账号。 */
    private final Long updatedBy;
}

package com.paytech.channel.connector.protocol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 创建或修改验证用例的已规范化输入；创建时 expectedVersion 为空。 */
@Getter
@AllArgsConstructor
public class SaveVerifyCaseRequest {

    /**
     * 操作者 Account ID（X-Gateway-Actor-Subject，规范正十进制）
     * <li>创建与修改必填；静态校验（V12）不带操作者，为 null。</li>
     */
    private final Long actorId;

    /** 路径中的渠道编码。 */
    private final String integrationCode;

    /** 用例编码；只校验时为空。 */
    private final String code;

    /** 名称，已去除首尾空白；只校验时为空。 */
    private final String name;

    /** 说明，空白已规范为 null。 */
    private final String description;

    /** 验证账号编码。 */
    private final String accountCode;

    /** 步骤。 */
    private final JsonNode steps;

    /** 修改时的期望版本；创建或只校验为空。 */
    private final Integer expectedVersion;

    /** 原因；只校验为空。 */
    private final String reason;
}

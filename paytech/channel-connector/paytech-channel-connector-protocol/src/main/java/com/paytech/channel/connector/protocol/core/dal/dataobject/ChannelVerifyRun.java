package com.paytech.channel.connector.protocol.core.dal.dataobject;

import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一次沙箱验证运行的结论与证据；不删除，用例删除后仍保留。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelVerifyRun {

    /** 运行内部技术标识，十进制字符串作为对外运行编号。 */
    private Long verifyRunId;

    /** 被运行的用例标识；用例删除后保留原值。 */
    private Long verifyCaseId;

    /** 所属渠道标识。 */
    private Long channelId;

    /** 运行结论。 */
    private VerifyRunStatus runStatus;

    /** 发起运行时的用例步骤、期望、账号编码与账号版本，不含凭据。 */
    private String caseSnapshotJson;

    /** 运行实际加载的配置版本；未加载配置时为空。 */
    private String configVersionsJson;

    /** 运行时出站地址及沙箱标记；未加载配置时为空。 */
    private String endpointsJson;

    /** 未通过时的首个原因；通过或运行中为空。 */
    private String failureSummary;

    /** 发起运行的 Operations 账号标识。 */
    private Long startedBy;

    /** 发起运行的 UTC 微秒时间。 */
    private Instant startTime;

    /** 得出结论的 UTC 微秒时间；运行中为空。 */
    private Instant finishTime;

    /** UTC 微秒创建时间。 */
    private Instant createTime;

    /** UTC 微秒更新时间。 */
    private Instant updateTime;
}

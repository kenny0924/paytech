package com.paytech.compliance.api.response;

import lombok.Getter;
import lombok.Setter;

/**
 * O14 运营程序目录中的一个程序版本
 * <li>在 CP-1 字段之上补充适用范围、程序与版本状态及批准信息；RETIRED 或 DRAFT 版本也会列出。</li>
 */
@Getter
@Setter
public class ClientReviewProgramResponseBody extends ProgramResolutionResponseBody {

    /** 主体类型：ORGANIZATION 或 INDIVIDUAL。 */
    private String partyType;

    /** 角色类型：CLIENT 或 RELATED_PERSON。 */
    private String roleType;

    /** 程序状态：DRAFT、ACTIVE 或 RETIRED；只有 ACTIVE 程序的 ACTIVE 版本参与解析。 */
    private String programStatus;

    /** 版本状态：DRAFT、ACTIVE 或 RETIRED。 */
    private String versionStatus;

    /** 批准该版本的 Operations Account ID，十进制字符串；未批准为空。 */
    private String approvedBy;

    /** 版本批准时间，ISO-8601 UTC；未批准为空。 */
    private String approvedTime;
}

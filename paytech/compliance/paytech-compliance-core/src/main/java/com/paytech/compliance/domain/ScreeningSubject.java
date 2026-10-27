package com.paytech.compliance.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 筛查需要的主体名称信息；只在内存中传给筛查渠道，不落库、不写日志。 */
@Getter
@AllArgsConstructor
public class ScreeningSubject {
    /** 资料 ID。 */
    private final long profileId;
    /** 主体类型：PERSON 或 ORGANIZATION。 */
    private final String subjectType;
    /** 检索全名。 */
    private final String fullName;
    /** 出生年份 YYYY；机构或未知为空。 */
    private final String birthYear;
    /** 相关国家（国籍、居住国或注册国），可为空列表。 */
    private final List<String> countries;
}

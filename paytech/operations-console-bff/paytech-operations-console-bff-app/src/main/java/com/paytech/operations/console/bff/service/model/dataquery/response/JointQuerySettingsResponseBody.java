package com.paytech.operations.console.bff.service.model.dataquery.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 联合查询页初始化数据：入口键类型、分组与护栏。 */
@Getter
@Setter
public class JointQuerySettingsResponseBody {

    /** 入口下拉，按 sortNo、keyTypeCode 升序；没有时为空列表。 */
    private List<JointQueryEntryKeyType> entryKeyTypes;

    /** 全部 ACTIVE 键类型的分组，按 rank 升序；用于空状态与链路图列头，页面不写死分组。 */
    private List<JointQueryGroup> groups;

    /** 护栏。 */
    private JointQueryLimits limits;
}

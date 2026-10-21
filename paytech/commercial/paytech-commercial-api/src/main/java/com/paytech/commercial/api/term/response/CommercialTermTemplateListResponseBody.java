package com.paytech.commercial.api.term.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 标准价目模板方案列表（按 OE 维护，规模有限，不分页）。 */
@Getter
@Setter
public class CommercialTermTemplateListResponseBody {
    /** 按 OE、方案编号升序 */
    private List<CommercialTermTemplateSummary> templates;
}

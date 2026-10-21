package com.paytech.reconciliation.service.processor.response;

import java.util.List;

import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import com.paytech.reconciliation.domain.LineRejection;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成导入结果：通过为 MATCHING；不通过为 REJECTED 并带逐行原因（只在首次完成时非空）。 */
@Getter
@AllArgsConstructor
public class CompleteChannelStatementResponse {
    /** 完成后的账单 */
    private final ChannelStatement statement;

    /** 逐行拒绝原因，按行号升序；整单原因的行号为空 */
    private final List<LineRejection> rejections;
}

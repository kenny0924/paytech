package com.paytech.risk.ext.rest.decision;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.api.decision.response.DictionaryWriteResponseBody;
import com.paytech.risk.api.decision.response.RejectionReasonResponseBody;
import com.paytech.risk.api.decision.response.SubjectTagResponseBody;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.Entry;
import com.paytech.risk.decision.domain.dictionary.DecisionDictionary.WriteResult;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;
import com.paytech.risk.decision.domainservice.service.DictionaryConflictException;

/** 只转换当前事实；不查询字典或修改不可变规则内容。 */
final class DecisionDictionaryWireConverter {
    /** 仅作为模型或转换方法的命名空间，不允许创建实例。 */
    private DecisionDictionaryWireConverter() { }
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);
    /** 将非空时间格式化为 UTC 六位小数；保留合同允许的 null。 */
    static String time(Instant time) { return time == null ? null : TIME.format(time); }
    /** 解析可选字典状态，仅接受 ACTIVE 或 DISABLED。 */
    static DictionaryStatus status(String value) {
        if (value == null) return null;
        for (var status : DictionaryStatus.values()) if (status.getCode().equals(value)) return status;
        throw new InvalidParameterException("字典状态无效");
    }
    /** 转换原因当前值，保留内部说明与默认文案的区别。 */
    static RejectionReasonResponseBody reason(Entry entry) {
        var response = new RejectionReasonResponseBody();
        response.setReasonCode(entry.code());
        response.setName(entry.name());
        response.setDescription(entry.description());
        response.setDefaultMessage(entry.defaultMessage());
        response.setStatus(entry.status().getCode());
        response.setVersion(Long.toString(entry.version()));
        response.setCreatedBy(Long.toString(entry.createdBy()));
        response.setUpdatedBy(Long.toString(entry.updatedBy()));
        response.setCreateTime(time(entry.createTime()));
        response.setUpdateTime(time(entry.updateTime()));
        return response;
    }
    /** 转换标签当前值，不添加适用主体字段。 */
    static SubjectTagResponseBody tag(Entry entry) {
        var response = new SubjectTagResponseBody();
        response.setTagCode(entry.code());
        response.setName(entry.name());
        response.setDescription(entry.description());
        response.setStatus(entry.status().getCode());
        response.setVersion(Long.toString(entry.version()));
        response.setCreatedBy(Long.toString(entry.createdBy()));
        response.setUpdatedBy(Long.toString(entry.updatedBy()));
        response.setCreateTime(time(entry.createTime()));
        response.setUpdateTime(time(entry.updateTime()));
        return response;
    }
    /** 转换本次写入结果，显式输出类型、代码、版本和变更人时间。 */
    static DictionaryWriteResponseBody result(WriteResult result) {
        var response = new DictionaryWriteResponseBody();
        response.setDictionaryType(result.type().getCode());
        response.setCode(result.code());
        response.setVersion(Long.toString(result.version()));
        response.setStatus(result.status().getCode());
        response.setChangedBy(Long.toString(result.changedBy()));
        response.setChangedTime(time(result.changedTime()));
        return response;
    }
    /** 将模块失败映射为已登记的安全业务错误，保留内部原因。 */
    static FailException failure(DictionaryConflictException failure) {
        return com.paytech.risk.service.convert.DecisionDictionaryExceptionConverter.convert(failure);
    }
}

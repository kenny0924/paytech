package com.paytech.risk.decision.api.response;

/** 本次字典事务的结果，不提供历史回执或请求重放。 */
public class DictionaryWriteResponseBody {

    /**
     * 字典类型。
     * <li>REJECTION_REASON 或 SUBJECT_TAG。</li>
     */
    private String dictionaryType;

    /**
     * 永久字典编码。
     * <li>停用后也不可复用。</li>
     */
    private String code;

    /**
     * 本次提交后的修订号。
     * <li>正 BIGINT 十进制字符串；创建为 1，更新或启停成功加一。</li>
     */
    private String version;

    /**
     * 本次提交后的状态。
     * <li>ACTIVE 或 DISABLED。</li>
     */
    private String status;

    /**
     * 本次可信操作者的 Account ID。
     * <li>正 BIGINT 十进制字符串。</li>
     */
    private String changedBy;

    /**
     * 本次变更时间。
     * <li>UTC RFC 3339 字符串，固定六位小数。</li>
     */
    private String changedTime;

    /** 返回字典类型。 */
    public String getDictionaryType() {
        return dictionaryType;
    }

    /** 设置字典类型。 */
    public void setDictionaryType(String dictionaryType) {
        this.dictionaryType = dictionaryType;
    }

    /** 返回永久字典编码。 */
    public String getCode() {
        return code;
    }

    /** 设置永久字典编码。 */
    public void setCode(String code) {
        this.code = code;
    }

    /** 返回本次提交后的修订号。 */
    public String getVersion() {
        return version;
    }

    /** 设置本次提交后的修订号。 */
    public void setVersion(String version) {
        this.version = version;
    }

    /** 返回本次提交后的状态。 */
    public String getStatus() {
        return status;
    }

    /** 设置本次提交后的状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 返回本次可信操作者的 Account ID。 */
    public String getChangedBy() {
        return changedBy;
    }

    /** 设置本次可信操作者的 Account ID。 */
    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    /** 返回本次变更时间。 */
    public String getChangedTime() {
        return changedTime;
    }

    /** 设置本次变更时间。 */
    public void setChangedTime(String changedTime) {
        this.changedTime = changedTime;
    }
}


package com.paytech.compliance.api.response;

/** 结算资格控制适用范围（t_settlement_control_scope 全部列）。 */
public class SettlementControlScopeResponseBody {

    /** 范围 ID（scope_id），十进制字符串。 */
    private String scopeId;

    /** 所属控制 ID（control_id），十进制字符串。 */
    private String controlId;

    /** 经营主体 ID（t_settlement_control_scope.operating_entity_id），十进制字符串；为空表示全部经营主体。 */
    private String operatingEntityId;

    /** 适用币种 ISO 4217 alpha-3（currency）；为空表示全部币种。 */
    private String currency;

    /** 开户银行国家 ISO 3166-1 alpha-2（bank_country）；为空表示全部国家。 */
    private String bankCountry;

    /** 范围哈希（scope_hash），SHA-256 小写十六进制。 */
    private String scopeHash;

    /** 创建时间（create_time），UTC 六位微秒。 */
    private String createTime;

    /** 读取范围 ID（scope_id），十进制字符串。 */
    public String getScopeId() {
        return scopeId;
    }

    /** 设置范围 ID（scope_id），十进制字符串。 */
    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    /** 读取所属控制 ID（control_id），十进制字符串。 */
    public String getControlId() {
        return controlId;
    }

    /** 设置所属控制 ID（control_id），十进制字符串。 */
    public void setControlId(String controlId) {
        this.controlId = controlId;
    }

    /** 读取经营主体 ID（t_settlement_control_scope.operating_entity_id），十进制字符串；为空表示全部经营主体。 */
    public String getOperatingEntityId() {
        return operatingEntityId;
    }

    /** 设置经营主体 ID（t_settlement_control_scope.operating_entity_id），十进制字符串；为空表示全部经营主体。 */
    public void setOperatingEntityId(String operatingEntityId) {
        this.operatingEntityId = operatingEntityId;
    }

    /** 读取适用币种 ISO 4217 alpha-3（currency）；为空表示全部币种。 */
    public String getCurrency() {
        return currency;
    }

    /** 设置适用币种 ISO 4217 alpha-3（currency）；为空表示全部币种。 */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /** 读取开户银行国家 ISO 3166-1 alpha-2（bank_country）；为空表示全部国家。 */
    public String getBankCountry() {
        return bankCountry;
    }

    /** 设置开户银行国家 ISO 3166-1 alpha-2（bank_country）；为空表示全部国家。 */
    public void setBankCountry(String bankCountry) {
        this.bankCountry = bankCountry;
    }

    /** 读取范围哈希（scope_hash），SHA-256 小写十六进制。 */
    public String getScopeHash() {
        return scopeHash;
    }

    /** 设置范围哈希（scope_hash），SHA-256 小写十六进制。 */
    public void setScopeHash(String scopeHash) {
        this.scopeHash = scopeHash;
    }

    /** 读取创建时间（create_time），UTC 六位微秒。 */
    public String getCreateTime() {
        return createTime;
    }

    /** 设置创建时间（create_time），UTC 六位微秒。 */
    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }
}

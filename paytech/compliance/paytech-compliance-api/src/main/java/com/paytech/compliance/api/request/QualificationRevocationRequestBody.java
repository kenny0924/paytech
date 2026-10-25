package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 运营撤销一条当前已批准的角色资格。 */
public class QualificationRevocationRequestBody {

    /**
     * 页面读取的资格版本
     * <li>十进制字符串；与当前版本不一致时 409 QUALIFICATION_CHANGED。</li>
     */
    @NotBlank
    @Pattern(regexp = "^[1-9][0-9]{0,18}$")
    private String expectedVersion;

    /** 撤销原因代码 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String reasonCode;

    /**
     * 撤销依据说明
     * <li>1—1000 字符；只供内部审计，不展示给客户。</li>
     */
    @NotBlank
    @Size(max = 1000)
    private String basisNote;

    /** 读取页面读取的资格版本。 */
    public String getExpectedVersion() {
        return expectedVersion;
    }

    /** 设置页面读取的资格版本。 */
    public void setExpectedVersion(String expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    /** 读取撤销原因代码。 */
    public String getReasonCode() {
        return reasonCode;
    }

    /** 设置撤销原因代码。 */
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    /** 读取撤销依据说明。 */
    public String getBasisNote() {
        return basisNote;
    }

    /** 设置撤销依据说明。 */
    public void setBasisNote(String basisNote) {
        this.basisNote = basisNote;
    }
}

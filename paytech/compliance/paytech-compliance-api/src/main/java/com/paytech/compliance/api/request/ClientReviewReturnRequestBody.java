package com.paytech.compliance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** O07 退回修改：原因、定位分区与客户可见说明。 */
public class ClientReviewReturnRequestBody {

    /** 退回原因代码。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$")
    private String reasonCode;

    /**
     * 退回定位分区
     * <li>BASIC、BUSINESS、PEOPLE 或 MATERIALS。</li>
     */
    @NotBlank
    @Pattern(regexp = "^(BASIC|BUSINESS|PEOPLE|MATERIALS)$")
    private String section;

    /** 客户可见说明，1—1000 字符。 */
    @NotBlank
    @Size(max = 1000)
    private String publicNote;

    /** 读取退回原因代码。 */
    public String getReasonCode() {
        return reasonCode;
    }

    /** 设置退回原因代码。 */
    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    /** 读取退回定位分区。 */
    public String getSection() {
        return section;
    }

    /** 设置退回定位分区。 */
    public void setSection(String section) {
        this.section = section;
    }

    /** 读取客户可见说明，1—1000 字符。 */
    public String getPublicNote() {
        return publicNote;
    }

    /** 设置客户可见说明，1—1000 字符。 */
    public void setPublicNote(String publicNote) {
        this.publicNote = publicNote;
    }
}

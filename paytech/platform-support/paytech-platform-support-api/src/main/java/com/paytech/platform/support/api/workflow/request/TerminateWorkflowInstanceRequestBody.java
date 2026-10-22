package com.paytech.platform.support.api.workflow.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 终止实例。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TerminateWorkflowInstanceRequestBody {

    /**
     * 终止原因
     * <li>必填，最长 512。</li>
     */
    @NotBlank
    @Size(max = 512)
    private String reason;
}

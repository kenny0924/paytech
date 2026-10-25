package com.paytech.compliance.service.processor.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.paytech.compliance.domain.QualificationSubject;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 批量资格判定；重复主体只判定一次。 */
@Getter
@AllArgsConstructor
public class EvaluateQualificationsRequest {
    /** 要判定的主体，1—50 个。 */
    @NotEmpty
    @Size(max = 50)
    private final List<@Valid QualificationSubject> subjects;
}

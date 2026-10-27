package com.paytech.compliance.service.processor;

import com.paytech.compliance.core.dal.dataobject.ProgramVersionView;
import com.paytech.compliance.domain.ResolvedProgram;
import com.paytech.compliance.service.processor.response.ProgramResolutionResponse;

/** 程序版本及模板到 Owner 结果的转换；CP-1 解析与运营程序目录共用。 */
final class ProgramResponseAssembler {
    private ProgramResponseAssembler() {
    }

    /**
     * 转换一个程序版本
     *
     * @param program 程序版本及模板
     * @return Owner 结果（含目录字段）
     */
    static ProgramResolutionResponse from(ResolvedProgram program) {
        ProgramVersionView version = program.getVersion();
        ProgramResolutionResponse response = new ProgramResolutionResponse();
        response.setProgramId(version.getProgramId());
        response.setProgramCode(version.getProgramCode());
        response.setProgramVersionId(version.getProgramVersionId());
        response.setVersionNo(version.getVersionNo());
        response.setQualificationType(version.getQualificationType());
        response.setJurisdictionCode(version.getJurisdictionCode());
        response.setTier(version.getTier());
        response.setOwnerPartnerId(version.getOwnerPartnerId());
        response.setReviewIntervalMonths(version.getReviewIntervalMonths());
        response.setFourEyesRequired(version.requiresFourEyes());
        response.setPartyType(version.getPartyType());
        response.setRoleType(version.getRoleType());
        response.setProgramStatus(version.getProgramStatus());
        response.setVersionStatus(version.getVersionStatus());
        response.setApprovedBy(version.getApprovedBy());
        response.setApprovedTime(version.getApprovedTime());
        response.setMaterials(program.getRequirements().stream().map(row -> new ProgramResolutionResponse.Material(
            row.getRequirementCode(), row.getSubjectRole(), row.getDocumentTypes(), row.isRequired(), row.getMaxAgeDays(),
            row.isWaivable())).toList());
        response.setChecks(program.getChecks().stream().map(row -> new ProgramResolutionResponse.Check(row.getCheckCode(),
            row.getCheckType(), row.getCheckMode(), row.getSubjectRole(), row.isBlocking(), row.getValidityDays())).toList());
        response.setScreenings(program.getScreenings().stream().map(row -> new ProgramResolutionResponse.Screening(
            row.getScreeningType(), row.getSubjectRole(), row.isRequired(), row.getMonitoringRequired() == 1)).toList());
        return response;
    }
}

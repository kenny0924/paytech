package com.paytech.operations.console.bff.ext.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.client.master.api.onboardingadmin.response.AdminCatalogItemResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminJurisdictionResponseBody;
import com.paytech.client.master.api.onboardingadmin.response.AdminOnboardingSchemaResponseBody;
import com.paytech.compliance.api.response.ClientReviewProgramListResponseBody;
import com.paytech.operations.console.bff.integration.ClientReviewRiskClient;
import com.paytech.operations.console.bff.integration.OnboardingAdminClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 入驻配置只读：辖区包、采集 Schema、目录项（client-master CM-10）与尽调程序（compliance O14）；单 Owner 转发
 * <li>路由权限 ONBOARDING_CONFIG_READ（块 138）；程序必须按辖区查询，避免超过 compliance 的 200 个版本上限。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/onboarding-config-admin/v1")
public class OnboardingConfigAdminController {
    private static final String JURISDICTION = "^[A-Z][A-Z0-9-]{1,15}$";

    private final OnboardingAdminClient client;
    private final ClientReviewRiskClient compliance;

    /** 全部辖区包与规则开关、组织类型、标识类型、关系类型。 */
    @GetMapping("/jurisdictions")
    public List<AdminJurisdictionResponseBody> jurisdictions(OperatorContext operator) {
        return client.jurisdictions(operator);
    }

    /** 某辖区的采集 Schema。 */
    @GetMapping("/onboarding-schemas")
    public List<AdminOnboardingSchemaResponseBody> schemas(OperatorContext operator, @RequestParam @Pattern(regexp = JURISDICTION) String jurisdiction) {
        return client.schemas(operator, jurisdiction);
    }

    /** 某辖区可见的尽调程序：该辖区与全局程序的全部版本（含 DRAFT / RETIRED）。 */
    @GetMapping("/programs")
    public ClientReviewProgramListResponseBody programs(OperatorContext operator, @RequestParam @Pattern(regexp = JURISDICTION) String jurisdiction) {
        return compliance.programs(operator, jurisdiction);
    }

    /** 某目录的目录项；给出辖区时为全局项与该辖区项。 */
    @GetMapping("/catalog-items")
    public List<AdminCatalogItemResponseBody> catalogItems(OperatorContext operator, @RequestParam @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,63}$") String catalogCode,
        @RequestParam(required = false) @Pattern(regexp = JURISDICTION) String jurisdiction) {
        return client.catalogItems(operator, catalogCode, jurisdiction);
    }
}

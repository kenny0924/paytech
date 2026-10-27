package com.paytech.client.master.api.onboardingadmin.response;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** CM-11 Client 详情：主档、当前资料（证件只给脱敏值）、关联人员、关系与申请历史。 */
@Getter
@Setter
public class AdminClientDetailResponseBody {
    /** Client 摘要。 */
    private AdminClientSummaryResponseBody client;

    /** 主主体当前资料，按草稿分区；identifier 只含类型、签发国、identifierMasked、检查结论与日期；contact 只含脱敏值；attributes 为扩展属性。 */
    private Map<String, Map<String, String>> values;

    /** 关联人员当前资料（同样不含证件原值）。 */
    private List<Person> people;

    /** 最近一份申请范围内的关系。 */
    private List<Relation> relations;

    /** 申请历史，新在前。 */
    private List<Application> applications;

    /** 一名关联人员。 */
    @Getter
    @Setter
    public static class Person {
        /** Party ID。 */
        private String partyId;

        /** 资料分区。 */
        private Map<String, Map<String, String>> values;
    }

    /** 一条关系。 */
    @Getter
    @Setter
    public static class Relation {
        /** 关系 ID。 */
        private String relationId;

        /** 关联人员 Party ID。 */
        private String relatedPartyId;

        /** 关系类型代码。 */
        private String relationType;

        /** 持股比例，可空。 */
        private String ownershipPercentage;

        /** UBO 认定依据，可空。 */
        private String uboBasis;

        /** 职务，可空。 */
        private String jobTitle;

        /** 状态。 */
        private String status;
    }

    /** 一份申请。 */
    @Getter
    @Setter
    public static class Application {
        /** 申请 ID。 */
        private String applicationId;

        /** 申请编号。 */
        private String applicationNo;

        /** 申请状态。 */
        private String status;

        /** 辖区。 */
        private String jurisdictionCode;

        /** 组织类型，可空。 */
        private String organizationTypeCode;

        /** 冻结的 Schema 版本。 */
        private String schemaId;

        /** 责任 PayFac，可空。 */
        private String responsiblePartnerId;

        /** 提交轮次。 */
        private int submissionRound;

        /** 当前审核任务，可空。 */
        private String dueDiligenceCaseId;

        /** 资格决定，可空。 */
        private String qualificationDecisionId;

        /** 最近提交 UTC 时间，可空。 */
        private String submittedAt;

        /** 最近更新 UTC 时间。 */
        private String updatedAt;
    }
}

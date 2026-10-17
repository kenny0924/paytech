package com.paytech.risk.test.builder.decision;

import com.paytech.risk.api.decision.request.ChangeDictionaryStatusRequestBody;
import com.paytech.risk.api.decision.request.CreateRejectionReasonRequestBody;
import com.paytech.risk.api.decision.request.CreateSubjectTagRequestBody;
import com.paytech.risk.api.decision.request.UpdateRejectionReasonRequestBody;
import com.paytech.risk.api.decision.request.UpdateSubjectTagRequestBody;
import com.paytech.risk.decision.domain.enums.DictionaryStatus;

/**
 * 字典写接口请求体的测试 Builder，复用正式请求 DTO
 * <li>默认值为最小合法内容，description 默认为 null（键必须出现）。</li>
 * <li>setter 原样保留 null、空串和非法格式，build() 不修复负例。</li>
 *
 * @author Kenny
 */
public final class DictionaryRequestBuilder {
    public static final String NAME = "人工复核";

    public static final String DEFAULT_MESSAGE = "默认内部消息";

    private DictionaryRequestBuilder() {}

    /** 创建拒绝原因。 */
    public static CreateReason createReason(String reasonCode) {
        return new CreateReason(reasonCode);
    }

    /** 完整更新拒绝原因。 */
    public static UpdateReason updateReason(long expectedVersion) {
        return new UpdateReason(Long.toString(expectedVersion));
    }

    /** 创建主体标签。 */
    public static CreateTag createTag(String tagCode) {
        return new CreateTag(tagCode);
    }

    /** 完整更新主体标签。 */
    public static UpdateTag updateTag(long expectedVersion) {
        return new UpdateTag(Long.toString(expectedVersion));
    }

    /** 启停字典。 */
    public static Status status(long expectedVersion, DictionaryStatus status) {
        return new Status(Long.toString(expectedVersion), status.getCode());
    }

    /** 创建拒绝原因请求。 */
    public static final class CreateReason {
        private final CreateRejectionReasonRequestBody body = new CreateRejectionReasonRequestBody();

        private CreateReason(String reasonCode) {
            body.setReasonCode(reasonCode);
            body.setName(NAME);
            body.setDescription(null);
            body.setDefaultMessage(DEFAULT_MESSAGE);
        }

        public CreateReason reasonCode(String reasonCode) {
            body.setReasonCode(reasonCode);
            return this;
        }

        public CreateReason name(String name) {
            body.setName(name);
            return this;
        }

        public CreateReason description(String description) {
            body.setDescription(description);
            return this;
        }

        public CreateReason defaultMessage(String defaultMessage) {
            body.setDefaultMessage(defaultMessage);
            return this;
        }

        public CreateRejectionReasonRequestBody build() {
            return body;
        }
    }

    /** 更新拒绝原因请求。 */
    public static final class UpdateReason {
        private final UpdateRejectionReasonRequestBody body = new UpdateRejectionReasonRequestBody();

        private UpdateReason(String expectedVersion) {
            body.setExpectedVersion(expectedVersion);
            body.setName(NAME);
            body.setDescription(null);
            body.setDefaultMessage(DEFAULT_MESSAGE);
        }

        public UpdateReason expectedVersion(String expectedVersion) {
            body.setExpectedVersion(expectedVersion);
            return this;
        }

        public UpdateReason name(String name) {
            body.setName(name);
            return this;
        }

        public UpdateReason description(String description) {
            body.setDescription(description);
            return this;
        }

        public UpdateReason defaultMessage(String defaultMessage) {
            body.setDefaultMessage(defaultMessage);
            return this;
        }

        public UpdateRejectionReasonRequestBody build() {
            return body;
        }
    }

    /** 创建主体标签请求。 */
    public static final class CreateTag {
        private final CreateSubjectTagRequestBody body = new CreateSubjectTagRequestBody();

        private CreateTag(String tagCode) {
            body.setTagCode(tagCode);
            body.setName(NAME);
            body.setDescription(null);
        }

        public CreateTag tagCode(String tagCode) {
            body.setTagCode(tagCode);
            return this;
        }

        public CreateTag name(String name) {
            body.setName(name);
            return this;
        }

        public CreateTag description(String description) {
            body.setDescription(description);
            return this;
        }

        public CreateSubjectTagRequestBody build() {
            return body;
        }
    }

    /** 更新主体标签请求。 */
    public static final class UpdateTag {
        private final UpdateSubjectTagRequestBody body = new UpdateSubjectTagRequestBody();

        private UpdateTag(String expectedVersion) {
            body.setExpectedVersion(expectedVersion);
            body.setName(NAME);
            body.setDescription(null);
        }

        public UpdateTag expectedVersion(String expectedVersion) {
            body.setExpectedVersion(expectedVersion);
            return this;
        }

        public UpdateTag name(String name) {
            body.setName(name);
            return this;
        }

        public UpdateTag description(String description) {
            body.setDescription(description);
            return this;
        }

        public UpdateSubjectTagRequestBody build() {
            return body;
        }
    }

    /** 启停请求。 */
    public static final class Status {
        private final ChangeDictionaryStatusRequestBody body = new ChangeDictionaryStatusRequestBody();

        private Status(String expectedVersion, String status) {
            body.setExpectedVersion(expectedVersion);
            body.setStatus(status);
        }

        public Status expectedVersion(String expectedVersion) {
            body.setExpectedVersion(expectedVersion);
            return this;
        }

        /** 原样设置线上状态值，可构造小写或未知状态负例。 */
        public Status status(String status) {
            body.setStatus(status);
            return this;
        }

        public ChangeDictionaryStatusRequestBody build() {
            return body;
        }
    }
}

-- 阶段3评审草稿；尚未获准执行，尚未并入正式PDManer模型或完整Schema。
-- 通用化后此文件仅作前版证据；Owner、分组和支付方式唯一键需重设计，见 ../../generic-execution-amendment.md。
-- 范围说明：保留前版API/公共方案两表；当前三表方向还包含独立脚本，本文件尚不完整。
-- 2张表、17列、2个主键、2个唯一索引；没有旧表变更和业务种子。
-- 执行前按 ../README.md 核对目标库、批准模型、备份与表不存在；禁止应用启动自动执行。

CREATE TABLE t_channel_api_definition (
    channel_api_id BIGINT NOT NULL COMMENT '一份渠道原生API定义的内部唯一标识，由Payment服务侧Snowflake生成。',
    channel_id BIGINT NOT NULL COMMENT '所属Payment Channel的内部唯一标识，引用同库t_payment_channel；创建后不可修改。',
    payment_method_code VARCHAR(64) NOT NULL COMMENT '本API唯一归属的精确支付方式代码，创建后不可修改。取值来源：Payment Method Catalog；非固定枚举，不允许通配符。',
    api_code VARCHAR(64) NOT NULL COMMENT '同一Channel和支付方式内稳定且唯一的渠道API代码，创建后不可修改。',
    api_name VARCHAR(128) NOT NULL COMMENT '供运营人员识别该渠道接口用途的名称。',
    description VARCHAR(512) NULL COMMENT '该渠道接口的用途说明；未提供时为空，不承载扩展字段或业务数据。',
    definition_json JSON NOT NULL COMMENT '严格ApiDefinition合同下的输入、处理步骤、HTTP传输、结果规则和输出定义，最大49152个UTF-8字节；禁止真实凭据和业务上下文原值。',
    create_time DATETIME(6) NOT NULL COMMENT '数据库记录创建的UTC微秒精度时刻。',
    update_time DATETIME(6) NOT NULL COMMENT '当前定义最近成功修改的UTC微秒精度时刻；每次修改严格递增，供期望旧值并发校验，不是发布版本。',
    PRIMARY KEY (channel_api_id),
    UNIQUE KEY uk_channel_api_code (channel_id, payment_method_code, api_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='渠道原生API当前定义';

CREATE TABLE t_channel_protocol_scheme (
    channel_scheme_id BIGINT NOT NULL COMMENT '一份渠道公共协议方案的内部唯一标识，由Payment服务侧Snowflake生成。',
    channel_id BIGINT NOT NULL COMMENT '所属Payment Channel的内部唯一标识，引用同库t_payment_channel；创建后不可修改，只允许同Channel引用。',
    scheme_code VARCHAR(64) NOT NULL COMMENT '同一Channel内稳定且唯一的公共协议方案代码，创建后不可修改。',
    scheme_name VARCHAR(128) NOT NULL COMMENT '供运营人员识别共享协议规则的名称。',
    description VARCHAR(512) NULL COMMENT '公共方案的用途及适用差异说明；未提供时为空，不承载扩展字段或业务数据。',
    definition_json JSON NOT NULL COMMENT '严格SchemeDefinition合同下的单能力预设或步骤组合，最大49152个UTF-8字节；仅保存凭据用途与受控规则，不保存真实Secret、Token、私钥或证书。',
    create_time DATETIME(6) NOT NULL COMMENT '数据库记录创建的UTC微秒精度时刻。',
    update_time DATETIME(6) NOT NULL COMMENT '当前方案最近成功修改的UTC微秒精度时刻；每次修改严格递增，供期望旧值并发校验，不是发布版本。',
    PRIMARY KEY (channel_scheme_id),
    UNIQUE KEY uk_channel_scheme_code (channel_id, scheme_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='渠道公共协议当前方案';

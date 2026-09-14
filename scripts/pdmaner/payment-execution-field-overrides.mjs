/** 已批准 Execution 字段的表级精确注释、默认值与排序规则。 */
export const EXECUTION_FIELD_OVERRIDES = Object.freeze({
  "payment_intent.consumer_country_code": {
    "comment": "Commerce 创建 Intent 时传入并冻结的消费者国家，用于支付定价；后续不得覆盖。取值来源：ISO 3166-1 alpha-2；非固定枚举。"
  },
  "payment_intent_binding.payment_intent_binding_id": {
    "comment": "来源订单与支付意图创建资格绑定的本库 Snowflake 唯一标识。"
  },
  "payment_intent_binding.source_type": {
    "comment": "支付来源的受控命名空间；本期只允许 COMMERCE_ORDER，不接收任意调用方自定义来源。取值来源：Payment Source Type Catalog；非固定枚举。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_intent_binding.source_id": {
    "comment": "来源命名空间中的稳定业务标识；COMMERCE_ORDER 时为 Commerce Order ID。"
  },
  "payment_intent_binding.client_id": {
    "comment": "来源订单所属 Client 标识；创建、关闭和查询必须核对同一 Client 权限。"
  },
  "payment_intent_binding.payment_intent_id": {
    "comment": "已创建的唯一支付意图标识；关闭先于创建时为空，非空后不得改变。"
  },
  "payment_intent_binding.closed_at": {
    "comment": "关闭新建意图、新 Attempt 和首次 AUTHORIZE/SALE 发送资格的 UTC 微秒时间；开放时为空，关闭不可撤销，不表示既有资金已经取消。"
  },
  "payment_intent_binding.create_time": {
    "comment": "数据库记录创建的 UTC 微秒精度时间，由应用写入。"
  },
  "payment_intent_binding.update_time": {
    "comment": "数据库记录最近一次持久化更新的 UTC 微秒精度时间，由应用写入。"
  },
  "payment_attempt.source_submission_id": {
    "comment": "Commerce 原支付提交命令标识；新执行必填且全局唯一绑定一个 Attempt，历史无可靠身份时为空，不得猜测回填。",
    "defaultValue": "NULL"
  },
  "payment_attempt.payment_goal": {
    "comment": "本次支付尝试冻结的业务目标；新执行必填且不可修改。允许值：PAY_NOW、AUTHORIZE_ONLY。历史未确定时为空。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "NULL"
  },
  "payment_attempt.authentication_requirement": {
    "comment": "内部 Risk 允许本次支付后冻结的认证要求。允许值：NOT_REQUIRED、THREE_DS_REQUIRED、CHALLENGE_REQUIRED。尚无确定允许结果时为空。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "NULL"
  },
  "payment_action.target_payment_action_id": {
    "comment": "QUERY 查询的原 Payment Action 标识；QUERY 必填并与原动作同父级、同渠道，其他动作为空。",
    "defaultValue": "NULL"
  },
  "payment_action.authentication_action_id": {
    "comment": "需要认证的 AUTHORIZE/SALE 绑定同 Attempt 的成功 AUTHENTICATE 标识；要求金额、币种、Card 和渠道坐标一致，其他动作为空。",
    "defaultValue": "NULL"
  },
  "payment_action.channel_operation_ref": {
    "comment": "渠道返回的稳定非 bearer 操作或认证上下文引用；未取得时为空；不得保存临时 URL、SDK Token、Client Secret 或持有即可继续支付的参数。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "NULL"
  },
  "payment_action.interaction_type": {
    "comment": "原 Action 的消费者交互类型，无交互时为空。允许值：REDIRECT、THREE_DS_CHALLENGE、SDK、APP_SWITCH、QR_CODE。本期只写 THREE_DS_CHALLENGE。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "NULL"
  },
  "payment_action.interaction_expires_at": {
    "comment": "原消费者交互可继续等待的 UTC 微秒截止时间；REQUIRES_ACTION 时必填，完成后保留历史值，不等于认证证明有效期。",
    "defaultValue": "NULL"
  },
  "payment_action.authentication_outcome": {
    "comment": "AUTHENTICATE 获得的可信标准化认证结论。允许值：AUTHENTICATED、ATTEMPTED、FAILED、UNAVAILABLE。无确定结论或非认证动作时为空。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "NULL"
  },
  "payment_action.challenge_performed": {
    "comment": "可信证据确认是否实际执行挑战：1 是、0 否；未知或非认证动作时为空，不能由浏览器回跳推断。",
    "defaultValue": "NULL"
  },
  "payment_action.authentication_valid_until": {
    "comment": "成功 AUTHENTICATE 证明可供原资金动作使用的 UTC 微秒截止时间；成功认证必填，其他情形为空。",
    "defaultValue": "NULL"
  },
  "payment_action.status_reason_code": {
    "comment": "原 Action 失败、未知或受控等待的稳定安全原因代码；无原因时为空；取值来源为 Execution Reason Catalog，不保存远程原始报文或敏感值。取值来源：Reason Catalog；非固定枚举。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "NULL"
  },
  "payment_action.action_type": {
    "comment": "原支付语义操作类型；认证无资金效应，查询只收敛原动作。允许值：AUTHENTICATE、AUTHORIZE、CAPTURE、SALE、VOID、REFUND、QUERY。无 REVERSAL 动作。"
  },
  "payment_action.target_channel_txn_ref_id": {
    "comment": "原成功资金交易引用；CAPTURE/VOID 指 AUTHORIZE，REFUND 指 SALE/CAPTURE，QUERY 在已有成功引用时可附带但仍以 target_payment_action_id 定位；初始支付及认证时为空。"
  },
  "payment_action.amount": {
    "comment": "当前动作绑定的确定正金额；AUTHENTICATE/QUERY 保存被认证或查询操作的金额用于一致性校验，不因此形成资金事实；本期精度上限为 24 位且小数最多 8 位。"
  },
  "payment_action.channel_idempotency_key": {
    "comment": "一次语义 Action 在原渠道上的稳定平台幂等键，重试不得改变；字段存在不代表渠道保证幂等，未知结果重发必须另经渠道幂等能力与期限校验。"
  },
  "compensation_event.sub_event_type": {
    "comment": "同一 Handler 下的受控业务子类型，最长 64 个 Java 字符；与 event_key、event_type 共同组成稳定唯一身份。取值来源：Payment Compensation Sub Event Catalog；非固定枚举。",
    "extProps": {
      "characterSet": "utf8mb4",
      "collation": "utf8mb4_0900_bin"
    }
  },
  "compensation_event.event_key": {
    "comment": "Handler 读取本服务权威事实使用的稳定业务键，最长 128 个 Java 字符；去首尾空白、拒绝控制字符，大小写敏感。",
    "extProps": {
      "characterSet": "utf8mb4",
      "collation": "utf8mb4_0900_bin"
    }
  },
  "compensation_event.event_type": {
    "comment": "全应用唯一注册的 Handler 类型，最长 64 个 Java 字符；去首尾空白、拒绝控制字符，大小写敏感。取值来源：Payment Event/Handler Catalog；非固定枚举。",
    "extProps": {
      "characterSet": "utf8mb4",
      "collation": "utf8mb4_0900_bin"
    }
  },
  "compensation_event.payload_json": {
    "comment": "可选最小 JSON Object，通常为空；最多 4 KiB UTF-8 字节由框架校验，不得保存可从本库恢复的上下文、卡数据、Vault Token、凭据或临时交互指令。",
    "extProps": {
      "characterSet": "utf8mb4",
      "collation": "utf8mb4_0900_bin"
    }
  },
  "compensation_event.execute_status": {
    "comment": "本地补偿任务执行状态。允许值：INIT、PROCESSING、ERROR、FAILED。成功后删除记录，FAILED 不改变业务资金状态。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "'INIT'"
  },
  "compensation_event.execute_count": {
    "comment": "已开始的 Handler 尝试次数，非负，Claim 时增加；达到上限后不得继续产生副作用。",
    "defaultValue": "0"
  },
  "compensation_event.next_execute_at": {
    "comment": "任务下次允许 Claim 的 UTC 微秒时间，由 Handler 退避策略更新。",
    "defaultValue": "CURRENT_TIMESTAMP(6)"
  },
  "compensation_event.last_error_summary": {
    "comment": "最近一次执行失败的脱敏摘要，最长 1000 字符；无错误时为空，不包含卡数据、凭据或其他敏感原值。"
  },
  "compensation_event.lock_version": {
    "comment": "Claim/Lease 使用的正乐观锁版本；登记初始值固定为 1，仅当前 Lease 持有者可回写执行结果。",
    "defaultValue": "1"
  },
  "compensation_event.create_time": {
    "comment": "本地补偿记录创建的 UTC 微秒时间。",
    "defaultValue": "CURRENT_TIMESTAMP(6)"
  },
  "compensation_event.update_time": {
    "comment": "本地补偿记录最近更新的 UTC 微秒时间，由写入口维护。",
    "defaultValue": "CURRENT_TIMESTAMP(6)"
  },
  "payment_instrument_token.payment_instrument_token_id": {
    "comment": "本次交易级支付工具绑定的 Snowflake 内部主键，供 Attempt 和清理任务定位。"
  },
  "payment_instrument_token.payment_instrument_token": {
    "comment": "安全随机生成的不透明内部支付工具定位符，原准备命令重放同值；仅持有此值不能获得用卡或扣款权限。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.client_id": {
    "comment": "从可信 Payment Intent 核对的所属 CCT Client 稳定标识，创建后不可修改。"
  },
  "payment_instrument_token.payment_intent_id": {
    "comment": "本次工具固定关联的 Payment Intent 内部主键，创建后不可修改。"
  },
  "payment_instrument_token.source_submission_id": {
    "comment": "Commerce 已提交的原支付命令全局稳定标识；当前仅接受 Commerce 命令命名空间，不是 Payment Attempt 标识。"
  },
  "payment_instrument_token.payment_method_code": {
    "comment": "本次工具绑定的精确支付方式，创建后不可修改。取值来源：Payment Method Catalog；非固定枚举。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.instrument_type": {
    "comment": "工具 Owner 的受控类型，本期只实现卡工具。允许值：CARD。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.instrument_ref": {
    "comment": "工具 Owner 返回的稳定内部记录引用，创建后不可修改；不包含原始卡数据、CVV、渠道工具 Token 或可直接使用的临时材料。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.expires_at": {
    "comment": "首次用卡资格的 UTC 微秒精度截止时间，创建时按原支付和材料期限冻结；重试不延长，不表示在途资金失效。"
  },
  "payment_instrument_token.revoked_at": {
    "comment": "主动撤销新用卡资格的 UTC 微秒精度时间；NULL 表示未主动撤销，仍须检查期限和原执行事实；不能据此撤销渠道资金。"
  },
  "payment_instrument_token.create_time": {
    "comment": "应用写入的工具绑定创建 UTC 微秒精度时间。"
  },
  "payment_instrument_token.update_time": {
    "comment": "应用写入的工具绑定最近有效更新 UTC 微秒精度时间。"
  },
  "payment_attempt.payment_instrument_token_id": {
    "comment": "本次尝试关联的交易级工具 Token 内部主键；新统一 pay 必填并校验相同 Client、Intent、来源提交和支付方式，历史未迁移行为空；不存 Token 字符串。"
  }
});

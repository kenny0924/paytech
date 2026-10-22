/** 已批准 Execution 字段的表级精确注释、默认值与排序规则。 */
export const EXECUTION_FIELD_OVERRIDES = Object.freeze({
  "command_idempotency.process_status": {
    "id": "0FE17297-EEAF-59F0-AB42-41FB6B60EBB1"
  },
  "command_idempotency.lock_owner": {
    "comment": "本次命令 Claim 的唯一持有者；pay 使用请求级随机 UUID，完成后由公共组件清空。"
  },
  "command_idempotency.lock_until": {
    "comment": "命令 Claim Lease 的 UTC 微秒截止时间；pay 仅覆盖本地短事务，完成 CAS 必须仍在租期内，完成后清空。"
  },
  "command_idempotency.version": {
    "comment": "Foundation 命令 Claim、Lease 和完成 CAS 使用的技术版本；首次为 1，独立于 Payment 业务根版本。"
  },
  "command_idempotency.response_code": {
    "comment": "Payment 命令的稳定回执代码；pay 使用 PAY_ACCEPTED 表示受理完成，不表示资金成功。取值来源：Payment Command Response Code Catalog；非固定枚举。"
  },
  "command_idempotency.idempotency_key": {
    "comment": "调用方在命令作用域内提供的大小写敏感幂等键；pay 固定为 submission 加冒号及原提交标识。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "command_idempotency.caller_scope": {
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "command_idempotency.resource_no": {
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "command_idempotency.response_location": {
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_intent.consumer_country_code": {
    "comment": "Commerce 创建 Intent 时传入并冻结的消费者国家，用于支付定价；后续不得覆盖。取值来源：ISO 3166-1 alpha-2；非固定枚举。"
  },
  "payment_attempt.source_submission_id": {
    "comment": "Commerce 原支付提交命令标识；新执行必填且全局唯一绑定一个 Attempt，历史无可靠身份时为空，不得猜测回填。",
    "defaultValue": "NULL"
  },
  "payment_action.interaction_type": {
    "comment": "原 Action 的消费者交互类型，无交互时为空。允许值：REDIRECT、THREE_DS_CHALLENGE、SDK、APP_SWITCH、QR_CODE。本期按渠道返回写 THREE_DS_CHALLENGE 或 REDIRECT；完成后保留历史值，可据此判断是否发生过挑战。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "NULL"
  },
  "payment_action.interaction_expires_at": {
    "comment": "原消费者交互的 UTC 微秒截止时间；PROCESSING 且需要交互时必填，完成后保留历史值，不等于认证证明有效期。",
    "defaultValue": "NULL"
  },
  "payment_action.action_type": {
    "comment": "原支付业务步骤类型；风控和认证不形成资金事实。允许值：PAY_RISK、AUTHENTICATE、AUTHORIZE、CAPTURE、PAY、VOID、REFUND。无 QUERY 或 REVERSAL 业务动作。"
  },
  "payment_action.action_status": {
    "comment": "支付业务动作的当前状态；创建即进入 PROCESSING，表示已进入处理流程，不代表已提交渠道。允许值：PROCESSING、UNKNOWN、SUCCEEDED、FAILED。"
  },
  "payment_action.target_channel_txn_ref_id": {
    "comment": "原成功资金交易引用；CAPTURE/VOID 指 AUTHORIZE，REFUND 指 PAY/CAPTURE；风控、认证与首次资金动作为空。"
  },
  "payment_action.amount": {
    "comment": "原动作冻结的正金额；PAY_RISK 与 AUTHENTICATE 仅作为输入校验，不产生资金事实；精度上限为 24 位且小数最多 8 位。"
  },
  "payment_action.channel_idempotency_key": {
    "comment": "Payment 为原渠道业务动作分配的全局唯一幂等键；PAY_RISK 为空，渠道动作必填；恢复不换键，不依赖可变渠道配置拼接新身份。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
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
  "payment_instrument_token.customer_id": {
    "comment": "本次支付关联的 Commerce Customer 标识，随申请请求传入；访客支付为 NULL。随 PaymentSucceeded v2 交给 Card 生成已存卡。"
  },
  "payment_instrument_token.consent_to_save": {
    "comment": "消费者是否同意本次支付成功后保存卡；1 表示同意，仅手输卡且有 customer_id 时可为 1。随 PaymentSucceeded v2 交给 Card。",
    "defaultValue": "0"
  },
  "payment_instrument_token.card_bin": {
    "comment": "Card 创建卡工具时返回的卡 BIN（6 或 8 位），只用作支付风控输入；非卡工具为 NULL。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.card_fingerprint": {
    "comment": "Card 创建卡工具时返回的跨交易稳定卡指纹（受限值，不能反推卡号），只经内部同步请求传给 Risk，不进日志或 MQ；非卡工具为 NULL。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.card_brand": {
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.funding_type": {
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.card_level": {
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.card_product": {
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.issuer_country": {
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    }
  },
  "payment_instrument_token.issuer_region": {
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
  },
  "payment_attempt.capture_mode": {
    "comment": "本次支付尝试冻结的捕获模式；新执行必填且不可修改。允许值：AUTOMATIC、MANUAL。历史未核对时为空。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "NULL"
  },
  "payment_attempt.active_action_id": {
    "comment": "原尝试当前唯一未终结业务动作；PROCESSING、UNKNOWN 占位，终态须核对仍指向原动作才清空；等待手动 Capture 时为空，但 Intent 继续占用该 Attempt。"
  },
  "payment_action.transaction_assessment_id": {
    "comment": "Risk 统一评估返回的评估 ID（evaluationId）；仅 PAY_RISK 取得结论后填写，已知后不可替换；渠道动作为空。"
  },
  "payment_action.risk_result": {
    "comment": "原 PAY_RISK 的可信评估结论；渠道动作及未知评估为空。允许值：ALLOW、REVIEW、DENY、FAILED、TIMED_OUT。评估完成不等于允许支付。"
  },
  "payment_attempt.channel_id": {
    "comment": "Routing 成功后冻结的支付渠道标识；路由前为空，三项渠道坐标同事务绑定后不可修改，后续动作和退款沿原 Attempt 使用。"
  },
  "payment_attempt.channel_account_id": {
    "comment": "Routing 成功后冻结的渠道技术账户标识；路由前为空，三项渠道坐标同事务绑定后不可修改，后续动作和退款沿原 Attempt 使用。"
  },
  "payment_attempt.mid_id": {
    "comment": "Routing 成功后冻结的渠道商户号标识；路由前为空，三项渠道坐标同事务绑定后不可修改，后续动作和退款沿原 Attempt 使用。"
  },
  "payment_action.authentication_requirement": {
    "comment": "仅原 PAY_RISK 获得可信 ALLOW 后冻结的认证要求；其他动作及未放行结果为空。允许值：NOT_REQUIRED、THREE_DS_REQUIRED、CHALLENGE_REQUIRED。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "NULL"
  },
  "payment_action.channel_response_code": {
    "comment": "渠道适配器返回的安全响应代码；仅渠道动作可填，风控结果使用 risk_result，本地异常或未取得响应时为空。取值来源：Channel Adapter Catalog；非固定枚举。",
    "extProps": {
      "characterSet": "ascii",
      "collation": "ascii_bin"
    },
    "defaultValue": "NULL"
  },
  "payment_action.channel_response_msg": {
    "comment": "渠道适配器清洗后的安全响应说明，最长512字符；不得保存原始报文、卡数据、个人信息、凭据或临时交互参数；不是状态机判断依据。",
    "defaultValue": "NULL"
  }
});

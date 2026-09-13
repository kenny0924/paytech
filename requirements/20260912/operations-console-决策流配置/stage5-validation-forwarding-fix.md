# 阶段 5：字段校验错误转发缺口

状态：用户已回复“都同意”，确认纳入必要修复及本地回归；实施和本地回归已完成，详见 [综合验证记录](./acceptance.md)。业务接口合同不变；不增加 API、表、字段或注册数据。

## 失败证据

通过真实 Gateway → BFF → Risk 保存一个结束节点缺少 `decisionCode` 的流程：Risk 返回 `422 VALIDATION_ERROR`，包含 `errors[].field/message`；BFF 返回 `502 UPSTREAM_INVALID_RESPONSE`。同一 Body 调用静态校验接口正常返回 `END_DECISION_REQUIRED`。

Risk 的 Problem 媒体类型、标准标题、请求 ID、六位小数时间及字段结构均符合现有 Profile。Foundation 的 `HttpServiceClientInterceptor.classifyClientCode` 只识别三个公共失败码以及注册的 Owner 错误码，遗漏公共 `VALIDATION_ERROR`；构造器同时禁止把公共码加入 Owner 注册，因此不能通过给 BFF 增加一个错误码枚举解决。

## 必要修复边界

1. 在 Foundation HTTP Client 的既有严格 Problem 解码之后，支持识别有效的 `422 VALIDATION_ERROR`，保留经过验证的字段定位供调用方转换。媒体类型、请求 ID、时间、状态及形状不合法时继续报协议错误。
2. BFF 将该明确的校验失败转换为自身 `422 VALIDATION_ERROR`，采用本地安全文案和 Body 路径投影，使用现有 Foundation Writer 生成新的时间和当前 Request ID。不得原样透传上游原始 Body、任意说明或未知字段路径；也不在 BFF 再写一套 Problem 解析器。
3. 沿用现有接口及依赖边界，公共组件不引入 Risk、节点、连线等业务类型。Foundation 通用能力仅补当前调用方需要的转换入口，不推广迁移其他系统。
4. 增加真实失败对应的 Foundation/BFF 回归：合法字段校验回到 422、恶意/非法 Problem 继续失败、其他公共码和 Owner 码不变；重新构建本机制品，再用真实链路复验。

这涉及原需求应用范围之外的 `paytech-foundation/paytech-http-client-support` 及必要的公共错误载体，与 BFF 消费适配。具体载体应优先复用现有公开扩展点，避免重复异常协议。允许本机构建/安装用于联调，不包含共享制品发布、test 部署、Git 提交或推送。

按 [需求交付流程第 2 节](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md#2-每次确认的规则)，新增公共组件范围须确认后实施；已确认的配置、历史和权限需求保持不变。


## 最终实现

Foundation 涉及现有 `paytech-http-client-support`、`paytech-http-problem`、`paytech-mvc-web-starter` 三个模块，无新增模块或依赖。前者默认关闭的 `withRequestValidationFailures()` 只允许显式 Body 的合法 422；BFF 只为 DecisionFlowManagementApi 启用并投影当前 Body，最终由现有 MVC Writer 输出。其余调用方行为保持原样。

Web 在保存的 `VALIDATION_ERROR` 或引用失效之后复用原静态校验 API，解决集合字段不能精确定位节点的问题；静态复查失败保留原字段提示，不丢失草稿。不升级 API 版本、不增加表或字段。

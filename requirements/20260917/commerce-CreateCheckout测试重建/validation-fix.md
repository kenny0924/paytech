# CreateCheckout 输入校验缺陷修复

用户提供两个失败用例日志，本轮按已确认的创建合同修复业务缺陷：当前仅支持 USD，商品 clientItemId 在同一订单内按原值大小写敏感唯一。没有改变测试期望来接受错误行为。

## 原因与修改

- 非 USD：原 RequestBody 只验证三位大写格式，EUR 完成预检及提交后，响应 Converter 才拒绝币种并返回 500。现在 Body 通过 Pattern 限制 USD，内部 Request 同样声明币种约束供公共 Processor 模板校验；移除 Converter 的迟到币种判断。HTTP 提前返回 422 VALIDATION_ERROR，errors 指向 currency。
- 重复商品编号：原流程未检查请求内唯一性，调用 CCT 后才触发数据库唯一键异常。现在 Request 封装无副作用的 `hasDuplicateClientItemIds`，Processor.validate 在金额校验和业务查询前拒绝重复，返回原有 422 VALIDATION_ERROR。数据库唯一索引保留。
- OpenAPI 同步创建币种枚举与商品编号唯一性描述。原两用例和零副作用断言保留，非 USD 用例额外检查 currency 字段错误。

## 分层与事务自查

Controller/Converter 仍只适配协议，基础币种约束由入口和公共模板触发；组合唯一性在所属 CreateCheckoutProcessor 校验，纯判断位于原 Request，不增加 Validator 或同义 DTO。CreateCheckoutContext、查询归属、业务防重与现有 Service 保存链保持：CCT 在事务外，本地五类事实在 REQUIRED 事务原子保存。没有 Repository/Mapper/SQL、状态、锁、请求幂等、View、生产 Mock 或对象回写变更，也没有新增提交后外部动作。

## 验证命令

使用 JDK 25，在 commerce-service 执行，已有两个用例先确认失败，再修改：

```bash
./mvnw -Pcheckout-create-tests '-Dtest=CreateCheckoutValidationTest#test_unsupportedCurrencyIsRejectedBeforePersistence+test_duplicateItemIdsAreRejectedBeforePersistence' test
./mvnw -Pcheckout-create-tests test
```

第一条在修改前后分别执行，第二条覆盖全部创建 HTTP 场景及原有事务用例。另解析 OpenAPI YAML 并检查 USD 枚举，核对相关差异空白。实际结果和日志统一见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-checkout-validation-20260917)。本轮未运行其他接口的全量 auto/覆盖率门禁，未执行 SQL、提交、推送、发布或部署。

# CreateCheckout 单元测试重建

## 目标与范围

按用户要求，删除 `ClientCheckoutApi#createCheckout` 原有测试，依照已确认的[单元测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)重建。入口为 Commerce 的 `POST /client-gateway-api/v1/checkouts`。

- 使用真实 Controller、Processor、Service、Repository/Mapper、本地 MySQL 和 CCT HTTP Client。
- 外部 CCT 通过独立测试 HTTP 服务返回用例配置的 DTO。
- 采用 Builder、Mocker、Checker；每例执行基类及测试类两级初始化，由基类统一清理全部自有数据和 Mock。
- 覆盖创建事实、金额及期限边界、业务订单唯一性、外部拒绝与故障、原子回滚及 REQUIRED 传播。
- 不实现 Gateway 请求 Key 重放，不迁移其他接口的测试，不为测试改变业务行为，不提交或部署。

## 验收标准

用例独立运行，不依赖前例；失败也清理。核对响应、真实持久化事实和外部调用；未配置请求必须导致用例失败。数据库及外部协议不足时明确失败，不用生产 Mock 或替代数据库掩盖。

实际执行结果、环境缺口和确认事项见[交付记录](delivery.md)及[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#commerce-create-checkout-tests-20260917)。

## 后续缺陷修复范围

用户随后提供两个实际失败日志，要求处理已暴露的非 USD 币种与重复商品编号问题。本轮按既有业务合同修复生产校验位置，并验证外部零调用、本地零写入；不改变币种支持范围或商品编号语义。详见[校验修复说明](validation-fix.md)。

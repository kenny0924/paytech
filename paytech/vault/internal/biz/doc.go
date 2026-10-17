// Package biz 是 Vault 的业务核心（≈ Java 的 Processor + Domain Service + domain + Repository 接口）。
//
//   - 用例：ProtectUsecase、RevealUsecase，按“校验 → 策略 → 事务外密码学 → 本地事务 → 审计”平铺；
//   - 领域能力：AccessAuditService（访问审计）、RecordCipherService（加解密与 HMAC）；
//   - 领域对象与错误码：Record、ProtectRequest、AccessEvent、ErrorCode 等；
//   - 端口：Store/TxStore（data 实现）、KeyService（pkg/localaes 实现）、Authorizer（pkg/policy 实现）。
//
// 本包不依赖 HTTP、数据库驱动或框架。
package biz

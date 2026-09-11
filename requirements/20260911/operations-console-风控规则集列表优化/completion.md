# 规则集列表优化：提交与交付归档

用户明确“先git提交一下”，授权按阶段 7 清单完成本轮本地提交；没有推送、创建分支/PR、重新部署或发布制品。当前长期状态见 [台账第 16 节](../../../agent-docs/CURRENT_IMPLEMENTATION.md#16-operations-规则集列表优化2026-09-11)。

| 仓库 | 本地提交 | 内容 |
|---|---|---|
| paytech-foundation | `6f2d692997e2` | fix(redis): defer resource lock meter registry initialization |
| risk-compliance-service | `d9f0c1c633a8` | feat(risk): manage rule sets as a whole with protected deletion |
| operations-console-bff | `2e0e8d707db0` | feat(operations): forward rule set deletion with dedicated permission |
| operations-console-web | `bf607aaaa961` | fix(operations): simplify rule set list and preserve browser navigation |
| platform-support-service | `ef6570f71833` | feat(platform): register operations rule set deletion access |
| Code-MoR | 本文所在归档提交 | 需求、模型/合同、SQL、验证及 test 发布记录 |

完整提交号与根仓库查询方式见 [提交记录](./assets/stage8-commits.json)。全部在原有 main 分支提交。Risk 的两个 Converter 文件按重命名呈现，因此 Git 汇总为 58 个文件变更，对应清单中的 60 个新增/删除路径。

## 提交前核验

BFF/Web/Foundation/Platform 的待提交文件 SHA-256 与阶段 7 清单一致。Risk 发现三份 API/Controller 的验收后格式调整，另有本需求开始前的两份源码 import 整理；按清单使用独立暂存内容分离，工作区源码没有被覆盖。

四份需要选择性提交的 Risk 源码分别以本轮暂存内容和当前工作区内容，在 JDK 25 下脱离业务运行环境编译；7 个生成 class 去除调试/源码元数据后，与 test 已部署 JAR 中的 class 完全一致，方法代码、签名、参数及注解一致。详细证据见 [选择性提交验证](./assets/stage8-risk-selective-verification.json)。初次独立编译发现暂存内容缺少一个新增枚举 import，已仅在暂存候选中补齐后重编译通过，没有修改用户工作区源码。

组件业务回归沿用阶段 4/5 和真实阶段 7 验收；本阶段不宣称重新运行全量 Java 测试。根仓库 Requirements Site 门禁、暂存范围、JSON/链接和凭据排查结果见 [提交检查](./assets/stage8-verification.json)。test 制品、数据库和容器在本阶段没有再次变更。

## 保留与后续

根仓库此前的公共需求流程、目录治理、上一项配置归档及 CCT 需求材料保留未提交；混合文档只选本轮规则集/API 命名变更。Risk 原有 import 整理及验收后的纯格式调整保持为未提交差异，未丢失。

关于隐藏冻结、最近修改人、详情业务化、限流恢复、名称搜索、复制等讨论仅为建议，没有纳入本轮研发或提交。已有旧 smoke 目录分类限制与真实规则执行/发布边界见 [部署报告](./deployment.md)。本阶段交付完成后由用户确认是否结束本需求，新的需求继续单独评审。

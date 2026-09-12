# RCO-16 阶段 5 综合验收

2026-09-11 本地综合验收完成。Risk 186 项、BFF 1040 项、Web 883 项、Platform Catalog 21 项通过；生产 Web 经真实 BFF/Risk 完成 22 个浏览器场景、84 次 API 调用，另以合成只读 HTTP 完成 4 个焦点检查，共 26 个场景，未捕获 JavaScript 异常。详细证据见 [验证摘要](./assets/stage5/verification.json)。

本轮规模保持阶段 3 批准范围：4 个系统，14 个新增接口、1 个原保存入口增加校验、2 张新表/21 列、2 个旧表索引、2 处旧字段注释及 182 条注册插入。本阶段没有新增业务 API、表或字段。

## 1. 本地结论与边界

- Web、BFF、Risk 使用当前未提交源码；Risk 连接仓库 local-environment 的 MySQL `127.0.0.1:13306/mor_risk`、Redis `16379`。应用版本、源码文件 Hash 与结果见验证摘要。
- Platform 授权使用项目既有 Simulator；浏览器登录、导航和可信 Gateway 请求头由合成夹具提供。读失败和写响应丢失场景显式注入错误，业务成功写入仍执行真实 BFF/Risk。不得把这些结果称为真实 Gateway/Platform 环境验收。
- 字典只有当前值管理，不增加回执或主体适用关系。原保存 Hash、历史回读和原幂等合同保持；Consumer 绑定尚不在原 Authoring Context 的实际能力中，本轮不新增运行时动作。
- Platform SQL 未应用、路由/资源未启用；没有 test 操作、部署、制品发布、Git 提交或推送。

## 2. 验收对照

| 验收 | 结果与证据 |
|---|---|
| AC-01 / 04 统一只读详情 | 当前详情和指定历史版本均进入只读编辑器；可切换规则、查看基本信息，无输入/保存控制。Authoring Context 不可用仍可查看已保存内容。[浏览器导航](./assets/stage5/browser-navigation.json)。 |
| AC-02 / 03 历史与精确版本 | 12 个历史版本、一集一行、RETIRED 展示、固定上界翻页、精确版本刷新不漂移；新存版本不改变旧上界。删除根普通详情/历史 404，字典反查仍可见。[Risk 回归](./assets/stage5/risk-tests.json)及浏览器导航。 |
| AC-05 导航 | 列表筛选→历史第 2 页→版本详情→Back/Forward；发布图/字典反查→旧版本→返回来源保留筛选；“返回列表”直接回列表，直接版本链接回受控历史页。[导航](./assets/stage5/browser-navigation.json)、[状态场景](./assets/stage5/browser-states.json)、[状态操作](./assets/stage5/browser-status.json)。 |
| AC-06 / 07 使用关系 | 当前草稿与旧版 ACTIVE 并存，旧版本仍保护整个规则集；筛选当前草稿得到空页时 inUse 仍为 true。图表使用同页数据；读失败保留旧图并显示未刷新，不冒充空结果。Risk 另覆盖 TEST/PRODUCTION 和断裂发布引用失败。 |
| AC-08 / 09 两类字典 | 创建、编辑、启停、永久唯一 code、CAS、可空说明；无主体类型字段。真实 HTTP 覆盖 CURRENT/HISTORY/DELETED 根引用及标签与普通同值字符串区分，浏览器完成维护与引用跳转。 |
| AC-10 编辑器接入 | 选择原因带默认文案，手工覆盖不会被目录读取改写，切换需明确确认；标签只能选择目录项，不能自由造值。仅使用原动态目标绑定，新增未知/停用项由 Risk 拒绝。[编辑器交互](./assets/stage5/browser-editor.json)。 |
| AC-11 兼容与回放 | 停用/缺失标签精确沿用及重排成功，复制动作/规则不获额外额度；修改原因消息拒绝，换为未登记目标由原能力门禁拒绝。当前仅一个可用目标绑定，不构造未来 Consumer 能力。旧格式空消息夹具真实回读不补默认，新保存仍拒绝。成功命令回放保持原响应。 |
| AC-11 / 12 未知结果 | 实际创建成功后丢失响应，Web 保留草稿、阻止重复操作，手动查询当前值/版本/更新人后恢复；CAS 冲突保留草稿，只有明确载入才替换。无字典幂等 Header 或自动重试/轮询。[字典交互](./assets/stage5/browser-dictionary.json)。 |
| AC-12 并发 | 两类字典分别覆盖保存先 SHARE、停用先 WRITE，共 4 个真实 MySQL/Redis/HTTP 锁顺序；协调器不替换 SQL 或业务结果。保存先行则两请求成功；停用先行则保存 409 且无版本/幂等残留。另有相同 CAS、同码创建和异码锁隔离回归。 |
| AC-12 权限 | BFF READ Permission 可取两类目录，六个写入口全部 403 且不请求 Risk；浏览器只读/配置用户看不到维护入口。真实 Gateway 可信身份与 Platform 授权数据待阶段 7 真实入口验收。 |
| 协议及原功能 | 新入口精确 Shape、Query/Body、16 KiB、Unicode、BIGINT、时间、GET 无 Body、错误白名单均有 Risk/BFF 门禁。原列表/保存/冻结/Candidate/删除与回放纳入全量回归。 |
| 注册闭包 | 生产 Catalog 编译器读取当前初始化 SQL，验证默认禁用及仅在内存启用新 14 路由两种状态；连同旧路由通过模板、Guard、Header/Query、Permission/Resource/父节点和管理员绑定检查。静态 DDL/DML 等价和模型门禁沿用阶段 4，有效工件未变；没有执行 Platform SQL。 |
| 页面状态与尺寸 | 1360、1024、390 px；1024 只读规则列表收起，窄屏单栏/满宽抽屉/图内横滚与表格；菜单 Enter/Space/方向键/Escape，抽屉键盘操作、只读文本；长文案、错误保留、空结果、迟到响应与草稿保护通过。 |

## 3. 本阶段发现与修复

1. 历史详情归属校验请求 `size=1` 被 BFF 公共分页门禁拒绝，改为 `size=10`，继续核对降序第一条的精确版本。
2. 字典同路由 Back 确认离开后仍保留旧编辑抽屉。以已提交的 location key 隔离页面实例；导航确认通过后清空旧草稿，未确认时仍保留。人工查询同时取消过期请求。
3. 历史第 2 页“返回列表”原来只退一条浏览器历史，回到历史第 1 页。改为明确导航到带原筛选的规则集列表；浏览器 Back/Forward 保持原历史语义。
4. 字典菜单打开和抽屉关闭后的焦点不完整，补齐菜单 autoFocus、关闭后回到当前列表触发按钮及抽屉 Tab 首尾循环。四种关闭路径与连续 20 次 Tab 检查通过，证据见[焦点检查](./assets/stage5/browser-focus.json)。

这四处产品修复只涉及 Web。Risk/BFF/Platform 本阶段增加验证覆盖，没有扩大业务范围。源码见 Web 的 `App.tsx`、`pages/RiskDictionary.tsx`、`risk/RuleSetExplorer.tsx`。

排查过程另外修正了验收夹具/脚本：原因规则需带当前配置形状；附加动作仍禁止冻结，发布夹具使用最初无附加动作版本；等待菜单/抽屉/Sider 动画稳定后截图与键盘取样；直接版本链接的已设计回退目标为历史页。没有因此放宽产品门禁或修改 CSS。

第一次 Risk 全量在浏览器夹具仍存在时，旧发布快照用例按空基线断言 1 条却读到 2 条，导致 1 项失败；精确清理后重跑 186/186 通过。未以此修改生产查询或删除不属于本轮的数据。

## 4. 命令、证据及清理

| 检查 | 最终结果 |
|---|---|
| Risk：JDK 25、Wrapper `./mvnw -q test`，本地 MySQL/Redis | 186/186，27 个测试类，0 失败/错误/跳过 |
| BFF：JDK 25、Wrapper `./mvnw -q test`，外部服务 Simulator | 1040/1040，0 失败/错误/跳过 |
| Web：`npm test`、`npm run build` | 883/883；TypeScript 与生产构建通过，保留原有大于 500 kB chunk 提示 |
| Platform：`./mvnw -q -pl platform-support-test -am -Dtest=RiskDictionaryRegistrationCatalogAutoTest,GatewayConfigurationCompilerAutoTest -Dsurefire.failIfNoSpecifiedTests=false test` | 21/21；不访问数据库 |
| 本机结构复核 | 两表 21 列及全部类型/空值/默认/注释/索引与模型一致，2 个旧表索引与注释一致 |
| 浏览器 | 26 个场景：22 个使用真实 BFF/Risk（84 次 API 请求），4 个使用合成只读 HTTP 检查焦点；0 JavaScript errors；合成授权/身份与故障注入边界如第 1 节 |

[实际测试清单](./assets/stage5/risk-tests.json)、[业务清理记录](./assets/stage5/local-cleanup.json)、[浏览器脚本快照说明](./assets/stage5/harness-snapshot/README.md)。原始临时日志位于 `/tmp/rco16-stage5/`，长期证据以本需求归档的安全摘要、源码快照和截图为准。

临时 Risk/BFF/Web 已停止；本轮 2 个合成规则集/13 个版本、所属字典及命令已精确清理。最终 Risk 规则、字典、发布及幂等相关 9 表均回到空基线，无其他 Risk 数据库连接。阶段 4 已批准的本机 Schema 保留，没有回滚 DDL。

截图：[只读详情](./assets/stage5/readonly-desktop-final.png)、[1024 布局](./assets/stage5/readonly-1024.png)、[手机只读](./assets/stage5/readonly-mobile.png)、[手机字典抽屉](./assets/stage5/tag-drawer-mobile.png)、[未知结果人工核对](./assets/stage5/dictionary-unknown.png)、[旧版本生效引用](./assets/stage5/active-old-version.png)。

当前停点：阶段 5 完成，等待用户确认阶段 6 test 发布准备。阶段 6 将核对真实 test 基线并形成具体制品、SQL、注册启用、备份恢复和验收清单，再按清单确认部署；本报告不自动授权后续环境变更。

# Client Console Web

本期认证页面：邮箱密码注册、查收/重发邮件、链接验证、登录、找回/重置密码，以及最小账号信息与退出。认证页面采用白底居中表单；入驻采用简洁单页，支持 390px 窄屏。资料和材料的阶段投影见[接口合同第 11 节](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#11-资料与材料页面的-bff-投影边界)，不把表单页面视为人工审核闭环。

认证文本输入统一复用 `src/TextField.tsx`；标签、红框、提示预留空间、密码显示和 ARIA 关联由组件维护。新增输入使用该组件；自定义校验表单使用 `noValidate`，规则集中在 `auth-form.ts`，提交时由页面校验并调用 `focusField`。不要复制登录页样式或依赖浏览器原生错误气泡。

可编辑设计仍为 [client-console.pen](./client-console.pen)，本期认证以 S01/S04 为准。完整需求见[设计入口](../requirements/20260914/client-console-注册与客户入驻/design.md)；实际验证及剩余工作只从[当前台账](../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)读取。

## 本地开发

使用与 Operations Web 相同的 Node/Vite/React 依赖基线，安装以 package-lock.json 为准。

```sh
npm ci
npm test
npm run build
npm run dev
```

开发预览地址为 `http://127.0.0.1:18302/login`。Vite 将 `/api/client` 转发至 `CLIENT_GATEWAY_URL` 指定的 Gateway，默认 `http://127.0.0.1:18080`。普通 HTTP 预览用于界面开发；真实会话须使用获批准的 HTTPS Origin、Gateway 路由和 Platform 认证配置，不能关闭 Secure Cookie 或修改 Origin 校验来凑通流程。

Access Token 只保存在内存；Refresh Token 由 BFF 的 HttpOnly Cookie 管理。单页刷新单飞，支持 Web Locks 的浏览器还会串行处理同 Origin 多标签的登录/刷新/退出请求。邮件 secret 从 fragment 读取后立即从地址栏移除，仅在明确提交时消费；刷新验证页后需重新打开邮件。

本期保留注册页填写密码、验证邮箱后再次登录的方案。密码与邮箱所有权绑定的加强方案已由用户延至对外开放前，见[决定记录](../requirements/20260914/client-console-注册与客户入驻/registration-password-binding-amendment.md)。

## 入驻页面（v2，2026-10-27 全球合规入驻切片 1）

`/onboarding` 列出工作区与申请；`/onboarding/new` 三步开始申请（PayFac 入驻码 → 注册国家与主体类型 → 准备清单，"开始填写"时才创建）；`/onboarding/applications/:id` 是任务清单首页，`/tasks/:task` 是各任务页，`/fix` 是退回后的补件视图；审核中、已批准、未通过、已逾期、已撤回分别是结果页。代码在 `src/onboarding/`：

- 表单完全由采集 Schema（CM-2）渲染：分区 → 分组 → 字段；控件注册表在 `widgets.tsx`，按国家变化的只有 Schema 数据（目录、地址组件、标识类型正则与校验位、可见条件、关系人规则、材料槽、声明）。字段键即草稿路径；同一 requirementGroup 的证件字段合并为一个"证件类型 + 号码"控件，号码只写不读，读回只有掩码。
- 需求清单（CM-7）决定任务状态；提交前核对与 422 统一为需求级 `errors[]`，页顶错误汇总逐条跳到字段 / 人员 / 材料 / 声明；文案按错误码本地化，不显示服务端原文。
- 材料直传文件服务，状态机：未上传 → 上传中（进度 + 取消）→ 检查中（扫描）→ 已完成；失败可重试或删除。关联只提交 `/files/v1/{fileId}`。
- 视觉按 N0 token（`src/style.css` 顶部 CSS 变量，IBM Plex 本地字体包）；状态只用圆点 + 灰字，不用彩色底框；动效时长与缓动见 token，遵守 prefers-reduced-motion。
- 声明每项一行，勾选即记录；带文档的声明（Schema `documentVersion`）可在侧边面板阅读并在确认时带上 `termsVersion`。条款正文随语言包发布在 `onboarding.legal.<声明类型>.<版本>.title|body`（段名小写，非字母数字转下划线），草案文本标"草案"；改版本时同时改 client-master DML 与语言包，覆盖测试核对 DML 里的版本都有正文。确认后改了资料的声明（`current=false`）显示失效、复选框回到未选，进度与核对按未完成计。受益人门槛文案按 `relationRules.uboThresholdComparison` 选"{n}% 或以上"（GTE）或"超过 {n}%"（GT，`_gt` 键）。
- 国家包文案在命名空间 `onboarding-schema`（`src/i18n/en/onboarding-schema.json`；client-master 初始化 DML 的 label_key 为 `onboardingSchema.*`，由 `schemaI18nKey()` 映射到 `onboarding-schema.*`，测试核对）；页面文案在 `onboarding`。

纯界面预览：先 `npm run build`，再运行需求目录的 `requirements/20261027/client-master-全球合规入驻与资格切片1/verification/preview-onboarding-v2.mjs`（loopback 模拟器，Schema 由仓库 DML 现场解析；文件直传不可用）。

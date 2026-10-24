# operations：运营控制台文件上传入口 · 需求（阶段 1 评审稿）

> 状态：2026-10-23 用户确认（Q1—Q5 按建议）。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景

[文件公开桶](../../20261022/platform-support-文件公开桶/requirement.md)上线后，Platform 支持公开上传：上传时声明 `accessLevel=PUBLIC`，文件按（来源系统，公开）写入规则写进公开桶，格式检查通过后返回长期有效的公开地址 `publicUrl` 和带版本的地址 `versionedUrl`。2026-10-23 mor-test 已接入 AWS 公开桶 `S3_TEST / mor-test-platform-public-k7q2`，并配置了默认规则 `* × 公开 → PUBLIC_DOCUMENT`（pdf / png / jpeg，20 MiB）。

但目前没有任何人工入口可以上传公开文件：
- 运营控制台只有卡 BIN CSV 和工作流附件两个上传入口，都只能上传私有文件，而且各自绑定了业务用途；
- 「文件记录」页只能查看、下载、替换已有文件，不能新建文件。

运营人员需要把 Logo、条款 PDF、说明图片这类本来就打算公开的素材上传到平台，拿到公开地址，再填到配置或对外文档里。

## 2. 目标

在运营控制台新增独立页面「文件上传」，开放**公开文件**的上传入口：
- 选择或拖入文件后，浏览器直接上传到对象存储；
- 等待格式检查完成；
- 检查通过后展示公开地址、带版本的地址和文件地址，可以一键复制。

## 3. 范围

| 编号 | 内容 |
|---|---|
| R1 | 新页面「文件上传」：放在「平台配置」菜单下、紧跟「文件记录」；有独立的页面查看权限和上传按钮权限 |
| R2 | 只上传公开文件。运营控制台后端固定声明 `accessLevel=PUBLIC`，页面上不提供选择 |
| R3 | 上传流程沿用现有的浏览器直传方式：开始上传 → 浏览器用临时授权 PUT 到对象存储 → 完成上传 → 轮询检查状态，直到检查通过（可用）或未通过 |
| R4 | 检查通过后展示 `publicUrl`、`versionedUrl` 和文件地址 `/files/v1/{fileId}`，三个都能复制，`publicUrl` 还能在新窗口打开；检查未通过时显示原因 |
| R5 | 上传前在页面上提示本次写入的限制：允许的类型、最大大小、目标存储。这些信息由运营控制台后端按写入规则解析（精确规则优先，没有就用 `*`）和生效的检查策略汇总；没有公开写入规则时提示「公开上传未配置」，并禁用上传 |
| R6 | 「最近上传」列表：近 7 天由运营控制台后端发起的公开上传，显示文件名、类型、大小、上传人、时间、检查状态、公开地址（可复制）；有「文件记录」查看权限的人员可以点进对应的文件详情 |
| R7 | Platform 注册数据：控制台资源（页面 + 按钮）、权限、运营控制台后端接口的网关路由，初始为停用状态，激活脚本单独提供 |

## 4. 不做

- 私有文件上传：运营控制台后端的私有写入规则目前被卡 BIN 占用，只允许 CSV。私有上传要单独协调，本期不做。
- 页面上不能选择存储实例、桶或检查策略；这些由写入规则决定，在「存储管理」里维护。
- 不做删除、批量上传、文件夹、CDN 刷新。替换文件继续在「文件记录」详情页里做。
- 不新增数据表，也不改 Platform 已有接口的字段。

## 5. 业务规则

| 编号 | 规则 |
|---|---|
| B1 | 来源系统由内部调用身份确定，固定为 `operations-console-bff`；上传原因固定为 `OPERATIONS_PUBLIC_ASSET`；上传人为当前会话账号。浏览器不能指定这三项 |
| B2 | 文件类型以浏览器声明的 MIME 类型为准，Platform 按生效的检查策略校验类型和大小；页面上的预校验只用来提示，以 Platform 的结果为准 |
| B3 | 公开地址只在检查通过后返回。检查中、检查未通过的文件不展示公开地址 |
| B4 | 没有（运营控制台后端，公开）规则、也没有（`*`，公开）规则时，Platform 返回 503；页面提示「公开上传未配置，请在存储管理中配置公开写入规则」，不会退回私有桶 |
| B5 | 临时 PUT 授权只出现在浏览器和运营控制台后端之间，运营控制台后端只记录元数据日志 |
| B6 | 上传按钮权限属于高敏感操作：上传后的内容任何人都能匿名读到。上传弹窗要显示提醒「文件将公开，任何人都能通过地址访问」 |

## 6. 验收标准

| 编号 | 场景 | 期望 |
|---|---|---|
| AC-01 | 有上传权限的人员上传 png 文件 | 检查通过后展示三个地址；`publicUrl` 不带凭据能读到内容 |
| AC-02 | 上传不允许的类型，或超过大小上限 | 开始上传时被拒，页面显示原因，不产生文件 |
| AC-03 | 没有任何公开写入规则 | 限制提示显示「公开上传未配置」，上传按钮禁用；直接调用接口返回 503 |
| AC-04 | 只有页面查看权限的人员 | 能看到页面和「最近上传」，没有上传按钮；直接调用上传接口返回 403 |
| AC-05 | 最近上传列表 | 包含刚上传的文件；有「文件记录」查看权限时能跳到对应详情 |
| AC-06 | 检查未通过（例如文件内容与声明类型不符） | 显示未通过，没有公开地址 |
| AC-07 | 运营控制台后端日志 | 不出现临时 PUT 授权地址 |
| AC-08 | 回归：卡 BIN 上传、工作流附件、文件记录 | 行为不变；运营控制台后端的私有写入规则不变 |

## 7. 逐系统变动点

### 7.1 operations-console-web

| 变动点 | 内容 |
|---|---|
| 页面 | 新增 `src/pages/FileUpload.tsx` 和 `src/fileUpload/*`：限制提示条、拖拽上传区、上传中状态（直传进度 → 检查中 → 结果）、结果卡片（三个地址，可复制 / 打开）、最近上传表格 |
| 导航 | `navigation/ConsolePage.tsx` 注册组件 `OPERATIONS_FILE_UPLOAD`，`pageCapabilities.ts` 增加页面能力；页面数量相关的测试同步更新 |
| 共用代码 | 复用卡 BIN 和工作流附件的直传方式（PUT 时不带 `If-None-Match`）、`RefreshButton`；写命令都声明 JSON Content-Type |
| 测试 | 严格解码器、请求体、状态轮询、权限按钮、未配置提示；`npm test` 和 `npm run build` 都要通过 |

### 7.2 operations-console-bff

| 变动点 | 内容 |
|---|---|
| 接口（新增） | `GET /file-upload/v1/constraints`：返回解析后的限制；`POST /file-upload/v1/uploads`：开始上传；`POST /file-upload/v1/uploads/{uploadId}/completion`：完成上传；`GET /file-upload/v1/files/{fileId}`：查询检查状态和地址；`GET /file-upload/v1/recent-uploads`：最近上传（路径在阶段 3 定稿） |
| 聚合 | 限制提示：从 Platform 读取写入规则和检查策略，在运营控制台后端的处理器层解析出精确规则或 `*` 规则；最近上传：按来源 `operations-console-bff` + 公开级别 + 近 7 天查询文件记录 |
| 外部调用 | 复用或扩展 Platform 内部文件接口的客户端（开始、完成、按 ID 查询），以及存储管理和文件记录接口的读取客户端 |
| 配置 | 严格请求体解析登记新请求类；`application.yml` 中开始上传的响应只记元数据日志 |
| 测试 | 以接口为入口的业务级测试：正常流程、403、未配置 503 透传、类型或大小被拒、日志不含授权地址；按 UNIT_TEST_GUIDE 用测试专用的 HTTP 端点返回 DTO |

### 7.3 platform-support-service

| 变动点 | 内容 |
|---|---|
| 接口（新增，D1 = A） | 内部只读接口 `GET /internal/files/v1/upload-constraints`、`GET /internal/files/v1/files`，按调用服务限定范围，详见 [technique.md](./technique.md) 第 5 节；`InternalFileApi` 只新增方法和响应类 |
| 处理器 / 服务 | `QueryFileUploadConstraintsProcessor`、`ListCallerFilesProcessor`；`FileUploadService.constraints`；`FileRecordQuery` 加 `uploaderAccountId`，Mapper 的 XML 增加对应条件 |
| 注册数据 | 块 136：权限 `FILE_UPLOAD_READ`（LOW）和 `FILE_UPLOAD_CREATE`（HIGH）；菜单 `OPERATIONS_FILE_UPLOAD` 和按钮 `OPERATIONS_FILE_UPLOAD_CREATE`；运营控制台后端 5 个路径的网关路由；初始停用，另附激活 / 停用脚本 |
| 存储配置 | 发布时新建（`operations-console-bff`，公开）→ AWS 公开桶 / `PUBLIC_DOCUMENT`（Q2） |
| 初始化 DML | `platform-support-initial-data.sql` 追加块 136 |
| 测试 | 内部接口的 auto 测试，见 technique.md 第 10 节 |

### 7.4 不受影响

Gateway（只消费 Platform 快照，不改代码）、card（卡 BIN 私有规则不动）、client-console-bff、client-master、多语言管理（code-mor-db 的 `platform-support × 公开` 精确规则不受影响）。

## 8. 待确认

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 页面权限默认授予哪些角色？ | 只授予平台管理员角色；其他角色在 IAM 里按需授予 |
| Q2 | 是否给运营控制台后端单独建一条公开精确规则？ | 建：目标 AWS 公开桶，检查策略 `PUBLIC_DOCUMENT`，与默认规则一致，但互不影响 |
| Q3 | 允许的类型够不够用？目前格式检查只能识别 pdf / png / jpeg / csv，svg、webp 等需要扩展 Platform 的格式检查器 | 本期按现有类型，扩展另立需求 |
| Q4 | 「最近上传」看谁的上传？ | 看所有人通过本入口的上传，可以按「只看我的」筛选 |
| Q5 | 是否需要原型？ | 页面简单，阶段 3 直接给出线框说明；如果需要，在 `operations-console.pen` 里加画板 |

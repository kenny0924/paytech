# Client Console Web

本期认证页面：邮箱密码注册、查收/重发邮件、链接验证、登录、找回/重置密码，以及最小账号信息与退出。认证页面采用白底居中表单；入驻采用简洁单页，支持 390px 窄屏。资料和材料的阶段投影见[接口合同第 11 节](../requirements/20260914/client-console-注册与客户入驻/api-contract.md#11-资料与材料页面的-bff-投影边界)，不把表单页面视为人工审核闭环。

认证文本输入统一复用 `src/TextField.tsx`；标签、红框、提示预留空间、密码显示和 ARIA 关联由组件维护。新增输入使用该组件；自定义校验表单使用 `noValidate`，规则集中在 `auth-form.ts`，提交时由页面校验并调用 `focusField`。不要复制登录页样式或依赖浏览器原生错误气泡。

可编辑设计仍为 [design.pen](./design.pen)，本期认证以 S01/S04 为准。完整需求见[设计入口](../requirements/20260914/client-console-注册与客户入驻/design.md)；实际验证及剩余工作只从[当前台账](../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)读取。

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

## 入驻页面

`/onboarding` 查询我的申请，用户显式选择个人/组织后才创建；`/onboarding/applications/:applicationId` 展示主表单、组织人员/关系编辑及材料。行业/MCC、资料版本、OTP、团队邀请不进入客户填写流程。组织类型、证件/机构与经营模式读取 CCT 受控目录；旧值保留并标明待确认，选项不可用不清空资料。正式提交及永久身份绑定仍按合同单独校验，目录可用和草稿保存成功均不代表提交就绪。

写操作冻结请求与幂等键，依赖失败可重试原操作；结果回读失败也不创建新意图。文件传输、完成确认、扫描与材料关联分阶段恢复。sessionStorage 仅保存当前账号＋申请的一份非敏感上传恢复坐标；不保存文件名、字节、表单或临时授权。扫描通过并经 CCT 当前授权核验后才关联。下载使用重新签发的短时 HTTPS 授权。

纯界面验证可运行需求目录的 `verification/preview-onboarding.mjs`（先 build）。它只监听随机 loopback，提供合成浏览器协议，不连接真实 Owner，不改变生产配置。正式业务仍必须走获准 HTTPS Gateway；生产前端没有旁路开关。复现步骤见[验证说明](../requirements/20260914/client-console-注册与客户入驻/verification/README.md#bffweb-入驻接线验证)。

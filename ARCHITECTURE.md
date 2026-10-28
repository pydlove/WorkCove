# 8小时工作台 架构与分包

## 1. 分层原则

参考 Regent 的模块边界，但缩小到个人工作台需要的范围：

```text
workcove-boot
├── workcove-interfaces
├── workcove-infrastructure
└── workcove-core
    └── workcove-commons
```

依赖只能向下：

- `workcove-core` 只包含纯 Java 的 `domain` 与 `application`。
- `workcove-infrastructure` 实现 `core` 定义的仓储和端口。
- `workcove-interfaces` 只调用 `core` 的应用服务，不直接访问 Mapper。
- `workcove-boot` 是唯一允许完成全部 Spring 装配的模块。
- `workcove-commons` 只放共享异常等基础构件，不依赖任何框架。

`ModuleBoundaryTest` 使用 ArchUnit 检查领域层和应用层不依赖 Spring、MyBatis、Servlet。

## 2. 身份上下文

```text
identity/
├── domain/
│   ├── model/          User、AuthSession、EmailVerificationCode 等
│   ├── port/           密码、验证码、会话令牌与邮件发送端口
│   └── repository/     User、AuthSession、EmailVerificationCode 仓储
├── application/
│   ├── command/        AuthService、EmailVerificationService
│   └── query/          登录用户与会话凭证
├── infrastructure/
│   ├── persistence/    MyBatis 用户、会话、验证码仓储
│   ├── security/       PBKDF2、SHA-256 验证码与会话令牌
│   └── mail/           SMTP 发送器与本地日志发送器
└── interfaces/
    ├── dto/            注册、登录与用户响应
    └── rest/           AuthController 与会话 Cookie 逻辑
```

验证码默认使用 `workcove.mail.mode=log` 输出到后端日志；配置 SMTP 后切换为
`workcove.mail.mode=smtp`。数据库只保存验证码和会话令牌的 SHA-256 摘要。

## 3. 数据库

MySQL schema：`workcove`

当前迁移：

- `V1__identity_foundation.sql`
  - `app_user`
  - `auth_session`
  - `email_verification_code`
- `V2__managed_server.sql`
  - `managed_server`
- `V3__managed_server_host_key.sql`
  - 首次连接后保存 SSH 主机密钥算法、指纹与公钥
- `V9__vault.sql`
  - `vault_entry`
  - `vault_unlock_session`
  - `vault_passkey`
  - `vault_webauthn_challenge`
- `V10__vault_categories.sql`
  - `vault_category`
- `V13__mail_accounts.sql`
  - `mail_account`

结构只通过 Flyway 演进。已执行的 `V` 脚本不修改，只能追加新版本。

## 4. Web 工程

```text
workcove-web/src/
├── api/                 HTTP 出口，按领域拆分
├── components/
│   ├── ui/              基础视觉组件（按钮、输入框、字段、分段控件等）
│   └── business/        业务组件
├── router/
├── services/            尚未迁移到后端的本地能力
├── stores/              Pinia 状态
├── styles/              tokens.css + global.css
├── types/               前端契约类型
└── views/               页面级组件
```

前端设计令牌集中在 `workcove-web/src/styles/tokens.css`，页面和业务组件必须遵循
[Web 前端样式规范](doc/开发规范/Web前端样式规范.md)。新增通用视觉控件先落
`components/ui/`，业务页面只负责组合和布局。

当前 `services/localAuth.ts` 是界面阶段的本地方案，等身份接口完成后删除，
登录状态统一改由 `/api/auth/me` 和后端 HttpOnly Cookie 驱动。

## 5. 桌面壳

`workcove-desktop` 使用 Electron，开启：

- `contextIsolation: true`
- `nodeIntegration: false`
- `sandbox: true`

窗口控制通过 preload 的有限桥接调用，不向页面暴露 Node.js。

开发版和客户端版必须保持独立：

- 开发版连接 Vite `localhost:32581`、后端 `8790` 和 MySQL `workcove`。
- 客户端版加载本机后端同源页面 `127.0.0.1:8890`，使用 MySQL `workcove_client`。
- Electron 用户目录、持久化 partition 和认证 Cookie 名称均按运行模式分开，禁止共享。
- 客户端内置精简 JRE、后端依赖库和瘦 JAR；Runtime 增量包只包含瘦 JAR、前端资源和版本清单。
- Runtime 更新必须校验 SHA-256，安装后只有在客户端成功加载时才能确认为健康，否则下次启动回滚。

## 6. 本地系统集成

目录选择和 IDE 启动属于宿主系统能力，不放业务页面直接执行：

- 网页模式的目录选择走 `DirectoryPicker` 端口，由 `MacDirectoryPicker` 在 infrastructure 层调用 macOS 原生目录对话框。
- 服务器密码使用 AES-GCM 加密后写入 `managed_server`，列表接口不返回密码或密文。
- 8小时工作台内置 SSH 终端使用 `SshTerminalGateway`，由 `sshj` 实现；浏览器只通过 WebSocket 传输终端输入输出，不接触服务器密码。
- 8小时工作台内置 SFTP 使用 `SftpGateway`，支持列目录、上传、下载、新建目录和删除；单文件传输上限为 200MB。
- SSH/SFTP 首次连接采用 TOFU 规则记录主机公钥和 SHA-256 指纹。后续指纹不一致时立即中止连接，避免静默接受主机变化。
- SSH WebSocket 握手校验 Origin、登录 Cookie、服务器 ID 与终端尺寸；REST SFTP 写操作校验登录 Cookie 和 `X-WorkCove-Action` 请求头。
- 服务器页面不展示第三方客户端入口，内置 SSH 与 SFTP 是当前唯一操作路径。
- 目录选择接口位于 `development/interfaces/rest`，必须校验登录 Cookie 和 `X-WorkCove-Action` 请求头。
- 邮件中心通过 Jakarta Mail 连接用户自己的 IMAP 和 SMTP 服务；同步时只抓取最近 200 封邮件头并写入 MySQL，列表和目录后续优先从缓存查询。
- 打开邮件时按需抓取正文、收件人和附件信息，并回填 MySQL；再次打开同一封邮件时直接读取缓存。
- 邮件列表提供回复、回复全部、转发、已读状态、星标、移动到目录、导出、删除和目录全部已读；写信窗口通过 SMTP 发送经过白名单清理的 HTML 与纯文本正文。
- 邮箱账号按用户和工作空间隔离，密码使用 AES-GCM 加密；账号列表只返回连接参数和“是否已有密码”，不回传密码或密文。
- 邮件 HTML 在浏览器端经过白名单清理，脚本、样式、表单和远程图片全部移除，避免跟踪像素和邮件内容破坏工作台布局。
- Git 仓库状态通过 `GitRepositoryGateway` 读取；分支、提交历史和工作区操作统一走受控命令白名单，不接受任意 shell 或任意 Git 参数。状态命令最长 8 秒，Fetch、Pull、Push 等网络操作最长 120 秒，输出上限为 1MB。
- Git 合并冲突由前端展示冲突文件，并可创建带仓库目录的 Codex 会话处理；Codex 不自动提交，继续合并和强制删除分支均需用户确认。
- Electron 模式通过 preload 暴露的受限方法选择目录、打开目录和启动 IntelliJ IDEA。
- IDEA 只从固定候选路径或 `WORKCOVE_IDEA_PATH` 启动，不接受任意可执行文件或命令参数。
- 工作目录中的“关闭”只移除 8小时工作台元数据，不删除本地文件，也不结束 IDEA 进程。

## 7. 用户数据隔离

所有用户产生的数据必须带明确的用户归属：

- 后端用户数据表必须包含 `owner_id` 或 `user_id`，仓储查询和更新必须同时带当前登录用户 ID。
- 服务器管理使用 `owner_id`；SSH、SFTP 和第三方打开入口都从当前登录会话取得用户 ID，跨用户访问返回“服务器不存在或无权访问”。
- 浏览器本地数据统一通过 `services/userScopedStorage.ts`，以规范化邮箱作为用户命名空间。
- 个人资料、网址分类、网址、点击量、应用快捷方式、本地工作目录、工作空间和随笔均使用该作用域。
- 主题偏好按用户分别保存；登录页默认主题和“上次登录邮箱”属于设备级偏好，不包含用户业务数据。
- `pnpm check:isolation` 会阻止绕过统一作用域直接新增全局 `localStorage` 读写。
- 预览账号使用独立命名空间，不与真实账号共享数据。

## 8. 密码库安全

- 密码条目使用 AES-GCM 加密后写入 `vault_entry`，列表接口只返回名称、用户名、分类、网址和备注。
- 密码明文通过按需接口单独读取，响应设置 `Cache-Control: no-store`，不进入列表、URL、日志或前端持久化。
- 保险库解锁支持 8小时工作台登录密码和 WebAuthn 平台认证；Touch ID 建议通过 `http://localhost:32581` 使用。
- WebAuthn 注册和验证挑战为一次性、两分钟有效，Origin 与 RP ID 必须在服务端白名单和校验范围内。
- 验证成功后签发独立 HttpOnly、SameSite Strict、路径限定 `/api/vault` 的五分钟解锁 Cookie。
- 密码分类按用户独立保存在 `vault_category`；分类重命名、删除迁移和条目查询都同时校验 `owner_id`。
- 所有条目查询、修改和删除都同时使用当前登录用户 ID 与保险库解锁会话；跨用户或过期会话一律拒绝。

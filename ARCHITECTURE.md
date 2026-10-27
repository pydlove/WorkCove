# WorkCove 栖作 架构与分包

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
│   ├── model/          User、Email、PasswordHash、UserId
│   ├── port/           PasswordHasher
│   └── repository/     UserRepository
├── application/
│   ├── command/        LoginCommand、RegisterEmailCommand
│   └── query/          AuthenticatedUser
├── infrastructure/
│   ├── persistence/    MyBatisUserRepository、UserMapper、UserData
│   └── security/       Pbkdf2PasswordHasher
└── interfaces/
    └── dto/            RegisterRequest、LoginRequest、AuthUserResponse
```

后续邮箱验证码、会话令牌、邮件发送分别进入同上下文的 `domain/port`、
`infrastructure/mail` 和 `interfaces/rest`。

## 3. 数据库

MySQL schema：`workcove`

当前迁移：

- `V1__identity_foundation.sql`
  - `app_user`
  - `auth_session`
  - `email_verification_code`

结构只通过 Flyway 演进。已执行的 `V` 脚本不修改，只能追加新版本。

## 4. Web 工程

```text
workcove-web/src/
├── api/                 HTTP 出口，按领域拆分
├── components/
│   ├── ui/              基础视觉组件
│   └── business/        业务组件
├── router/
├── services/            尚未迁移到后端的本地能力
├── stores/              Pinia 状态
├── styles/              tokens.css + global.css
├── types/               前端契约类型
└── views/               页面级组件
```

当前 `services/localAuth.ts` 是界面阶段的本地方案，等身份接口完成后删除，
登录状态统一改由 `/api/auth/me` 和后端 HttpOnly Cookie 驱动。

## 5. 桌面壳

`workcove-desktop` 使用 Electron，开启：

- `contextIsolation: true`
- `nodeIntegration: false`
- `sandbox: true`

窗口控制通过 preload 的有限桥接调用，不向页面暴露 Node.js。

# WorkCove 栖作

本地优先的个人工作台，用于辅助日常工作。项目结构参考 Regent，但只保留个人工作台需要的能力。

## 工程结构

```text
WorkCove/
├── workcove-commons/       # 共享异常与基础构件
├── workcove-core/          # 纯 Java 领域层与应用层
├── workcove-infrastructure/# MyBatis、Flyway、邮件与安全实现
├── workcove-interfaces/    # REST Controller、DTO
├── workcove-boot/          # 唯一启动入口与配置
├── workcove-web/           # Vue Web 工程
└── workcove-desktop/       # Electron 桌面壳
```

## 本地运行

MySQL 需要 `workcove` schema 和受限账号 `workcove_app`。本地密码只放在
`workcove-boot/src/main/resources/application-local.yml`，该文件不进入版本库。

推荐使用本地启停脚本，前后端日志统一写入 `log/`：

```bash
./deploy/local/start-all.sh
./deploy/local/status.sh
./deploy/local/stop-all.sh
```

日志文件：

```text
log/backend.log
log/frontend.log
log/backend-build.log
log/frontend-install.log
```

也可以手动启动。

```bash
mvn clean package
java -jar workcove-boot/target/workcove-boot-0.1.0-SNAPSHOT.jar
```

后端默认监听 `127.0.0.1:8790`。

```bash
cd workcove-web
pnpm install
pnpm dev
```

前端默认监听 `127.0.0.1:32581`，并将 `/api` 代理到后端。

### 邮箱验证码

本地默认使用 `log` 模式，验证码会写入后端日志：

```text
WorkCove development email code: email=..., code=...
```

真实发送邮件时设置 SMTP 环境变量：

```bash
export WORKCOVE_MAIL_MODE=smtp
export WORKCOVE_MAIL_HOST=smtp.126.com
export WORKCOVE_MAIL_PORT=465
export WORKCOVE_MAIL_USERNAME=your-account@126.com
export WORKCOVE_MAIL_PASSWORD=your-smtp-password
```

SMTP 密码只放在本机环境或未入库的本地配置中，不提交到 Git。

### 配置加密

WorkCove 使用 AES-256-GCM 加密本地敏感配置，格式为 `ENC(v1:...)`。

- 主密钥默认位于 `~/.workcove/config.key`，权限必须为 `600`
- 也可通过 `WORKCOVE_CONFIG_KEY` 提供
- 生成密文：

```bash
printf '%s' '需要加密的值' | node scripts/encrypt-config.mjs encrypt
```

`application-local.yml` 中的数据库密码、SMTP 账号和 SMTP 密码均使用密文保存。
企业网络额外需要的 SMTP CA 信任库位于 `~/.workcove/mail-truststore.p12`。

配置模板见 `workcove-boot/src/main/resources/application-local.yml.example`。

## 质量检查

```bash
mvn clean verify
cd workcove-web && pnpm typecheck && pnpm build
```

## 分支管理

项目使用 Git Flow。`main` 保存稳定版本，`develop` 用于日常集成。

```bash
brew install git-flow-next
git flow feature start WCV-123-example
git flow feature finish WCV-123-example
```

完整规范见 [Git 分支规范](doc/开发规范/Git分支规范.md)。

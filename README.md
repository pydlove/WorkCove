# 8小时工作台

本地优先的个人工作平台，用于辅助日常专注工作。项目结构参考 Regent，但只保留个人工作平台需要的能力。

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

前端默认通过 `http://localhost:32581` 访问，并将 `/api` 与 `/ws` 代理到后端。
Touch ID 依赖 WebAuthn 安全上下文，请使用 `localhost` 而不是 `127.0.0.1`。

## 开发版与客户端版

两套环境必须同时可用：

| 项目 | 开发版 | 客户端版 |
|---|---|---|
| 后端端口 | `8790` | `8890` |
| 前端入口 | Vite `localhost:32581` | 内置静态资源 `127.0.0.1:8890` |
| MySQL schema | `workcove` | `workcove_client` |
| Electron 用户目录 | `com.pydlove.workcove.dev` | `com.pydlove.workcove` |
| 登录 Cookie | `workcove_session` | `workcove_client_session` |

客户端首次安装前，以 MySQL 管理员执行一次：

```bash
./deploy/local/setup-client-db.sh
```

客户端会内置 JRE、后端依赖库、瘦 JAR 和前端资源。运行时增量包只更新瘦 JAR
与前端资源，不重复分发 Java 依赖。

构建与升级说明见 [Desktop README](workcove-desktop/README.md)。

### 邮箱验证码

本地默认使用 `log` 模式，验证码会写入后端日志：

```text
8小时工作台 development email code: email=..., code=...
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

8小时工作台使用 AES-256-GCM 加密本地敏感配置，格式为 `ENC(v1:...)`。

- 主密钥默认位于 `~/.workcove/config.key`，权限必须为 `600`
- 也可通过 `WORKCOVE_CONFIG_KEY` 提供
- 生成密文：

```bash
printf '%s' '需要加密的值' | node scripts/encrypt-config.mjs encrypt
```

`application-local.yml` 中的数据库密码、SMTP 账号和 SMTP 密码均使用密文保存。
企业网络额外需要的 SMTP CA 信任库位于 `~/.workcove/mail-truststore.p12`。

邮件中心同步目录和最近 200 封邮件头到客户端 MySQL，邮件列表默认从 MySQL 查询；
首次打开单封邮件时按需读取正文并回填缓存，后续访问直接使用数据库缓存。

配置模板见 `workcove-boot/src/main/resources/application-local.yml.example`。

## 质量检查

```bash
mvn clean verify
cd workcove-web
pnpm check:ui
pnpm check:isolation
pnpm typecheck
pnpm build
```

前端样式、组件和响应式规则见
[Web 前端样式规范](doc/开发规范/Web前端样式规范.md)。

“App”页面支持本地应用管理，并通过宿主进程批量检测应用是否已经打开；
窗口重新聚焦、页面恢复可见以及固定间隔都会刷新运行状态。

工作空间支持按账号保存自定义图标：上传 PNG、JPEG 或 WebP 图片后会居中裁切并压缩为方形图标，
不再使用预置图标和颜色组合。

工作台“今日关怀”支持记录喝水、起身活动和洗手间次数，连续工作后提醒补水与活动。
底部支持每天一次上班打卡和下班打卡，下班后再次点击只更新当天打卡时间；“休息一下”会进入全屏休息计时，点击继续工作后自动记录休息区间，
并从净工作时长中扣除。提醒设置支持按周一至周日分别配置上下班时间、工作日开关、上下班打卡提醒和提前提醒时间。
关怀记录提供今日与最近 30 天工作时间曲线、打卡和休息详情。

“研发 → Git 仓库”支持三种登记方式：手动填写尚未克隆的远端仓库地址、
从已登记工作目录选择、或直接选择任意本地 Git 文件夹。列表展示当前分支、
远端、领先/落后提交、暂存区、工作区变更和最近提交；仓库通过工作空间级分类
整理，分类支持新增、重命名和删除迁移。远端记录可在克隆后通过本地文件夹补全，
移除仓库记录不会删除本地代码。

“研发 → 服务器管理”支持在 8小时工作台内直接打开 SSH 终端和 SFTP 文件管理。
密码使用本机配置主密钥做 AES-GCM 加密后保存在后端，不会发送到浏览器、
URL 或日志；首次连接采用 TOFU 规则记录 SSH 主机指纹，指纹变化时拒绝连接。

服务器列表只提供 8小时工作台内置 SSH 与 SFTP 操作，不展示第三方客户端入口。

“工具 → 速记”提供工作记录流，支持 `Command + Shift + N` 快速记录、自动保存、
搜索、置顶、重要标记、已解决筛选、标签、归档和删除确认。随笔按工作空间和登录账号隔离。

“工具 → 密码库”支持账号密码集中管理。每次查看前需使用登录密码或 Touch ID 验证，
验证后 5 分钟内免重复验证；密码用 AES-GCM 加密保存，列表不返回密码明文。
密码分类支持新增、重命名、删除迁移和快捷移动，分类与条目均按登录账号隔离。

用户资料、网址、分类、应用快捷方式、工作目录、Git 仓库、随笔、密码库、关怀记录、主题和字体偏好均按登录账号隔离。
统一约束见 [架构文档](ARCHITECTURE.md) 与 [前端样式规范](doc/开发规范/Web前端样式规范.md)。

## 分支管理

项目使用 Git Flow。`main` 保存稳定版本，`develop` 用于日常集成。

```bash
brew install git-flow-next
git flow feature start WCV-123-example
git flow feature finish WCV-123-example
```

完整规范见 [Git 分支规范](doc/开发规范/Git分支规范.md)。

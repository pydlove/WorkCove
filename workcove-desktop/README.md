# 8小时工作台 Desktop

Electron 桌面壳。开发版与客户端版使用独立的端口、Cookie、Electron 用户目录和 MySQL schema，可以同时运行。

## 开发版

开发版连接 Vite 与仓库内开发后端：

- 前端：`http://127.0.0.1:32581`
- 后端：`http://127.0.0.1:8790`
- MySQL：`workcove`
- Electron 用户目录：`com.pydlove.workcove.dev`

```bash
pnpm install
pnpm start
```

可通过 `WORKCOVE_WEB_URL` 覆盖开发前端地址。

## 客户端版

客户端内置精简 JRE、后端依赖库、瘦 JAR 和前端静态资源：

- 客户端入口：`http://127.0.0.1:8890`
- MySQL：`workcove_client`
- Electron 用户目录：`com.pydlove.workcove`
- Cookie：`workcove_client_session` / `workcove_client_vault`

首次安装前，需要由 MySQL 管理员执行一次：

```bash
./deploy/local/setup-client-db.sh
```

脚本会创建 `workcove_client` 并授权给现有 `workcove_app` 账号。也
可以直接执行 `deploy/mysql/setup-client-db.sql`。

客户端包沿用本机已有的 `~/.workcove/config.key` 解密数据库和邮件配置。
如果换到另一台 Mac，需要同时提供匹配的主密钥，不能只复制 `.app`。

## 构建

客户端发布必须由用户在当前对话中明确授权。普通开发任务只更新研发
环境，不得在功能完成或测试通过后自动发布客户端。未获得授权的任务
不得执行 `build:release`、`build:runtime` 或 `publish:local`。

```bash
# 完整安装包 + 内置 Runtime + 更新清单
pnpm build:release

# 只构建增量 Runtime，必须提供新的发布码
WORKCOVE_RELEASE_CODE=R100B002 pnpm build:runtime

# 仅在用户明确授权更新客户端后，将构建产物发布到更新目录
WORKCOVE_CLIENT_UPDATE_APPROVED=1 \
  pnpm publish:local -- --user-approved
```

直接运行 `pnpm publish:local` 会被脚本拒绝，避免普通任务误更新本机
客户端。

产物位于 `dist/`：

- `WorkCove-<version>-<releaseCode>-macOS-<arch>.dmg`
- `WorkCove-<version>-<releaseCode>-runtime.zip`
- `latest.json`

完整客户端包含 JRE 和后端依赖，普通升级的 Runtime 只包含约 18KB 的瘦
JAR、前端资源和版本清单，通常约 1-3MB。

## 增量升级

客户端启动后会从自身本机后端读取 `/api/release/latest`，并在发现更高发布码时提示更新。Runtime 下载后会校验 SHA-256，解压到：

```text
~/Library/Application Support/com.pydlove.workcove/runtime/releases
```

新 Runtime 启动失败时会自动回滚。Java、Electron 或后端依赖库发生变化时，需要发布新的完整客户端壳；仅网页和后端业务代码变化时，使用 `build:runtime` 即可。

如果更新文件放在远程服务器，可创建：

```text
~/Library/Application Support/com.pydlove.workcove/update-source.json
```

```json
{
  "manifestUrl": "https://example.com/workcove/latest.json"
}
```

也可通过 `WORKCOVE_UPDATE_MANIFEST_URL` 覆盖。未配置时使用本机后端更新目录。

## 本地开发集成

- “工作目录”中的目录选择使用 macOS 原生目录对话框。
- “IDEA”操作会查找本机 `IntelliJ IDEA.app`，按项目目录启动 IDE。
- 非标准安装位置可通过 `WORKCOVE_IDEA_PATH` 指定 IDEA 可执行文件。
- 当前“关闭”表示从 8小时工作台工作目录列表移除，不删除项目文件，也不结束已经运行的 IDEA 进程。

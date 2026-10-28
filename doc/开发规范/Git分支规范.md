# Git 分支与 Git Flow 规范

适用范围：8小时工作台全部代码、文档和发布流程。

工具版本：`git-flow-next 2.1.0`

## 1. 分支模型

仓库使用经典 Git Flow：

| 分支 | 类型 | 来源 | 合并目标 | 用途 |
|---|---|---|---|---|
| `main` | 长期分支 | 初始提交 | 无 | 生产稳定版本，必须随时可发布 |
| `develop` | 长期分支 | `main` | 无 | 日常集成分支，所有功能先在合并到这里 |
| `feature/<name>` | 短期分支 | `develop` | `develop` | 新功能 |
| `bugfix/<name>` | 短期分支 | `develop` | `develop` | 非紧急缺陷修复 |
| `release/<version>` | 短期分支 | `develop` | `main`、`develop` | 版本冻结、验收和发布准备 |
| `hotfix/<version>` | 短期分支 | `main` | `main`、`develop` | 生产环境紧急修复 |
| `support/<name>` | 长期分支 | `main` | 无 | 旧主版本长期维护，当前阶段不启用 |

禁止在 `main` 上直接开发。

`develop` 允许通过 Git Flow 合并功能分支。启用 GitHub 分支保护后，`main` 和 `develop` 都应由拉取请求或 Git Flow 完成更新。

## 2. 命名规范

分支描述部分统一使用小写英文、数字和连字符：

```text
feature/WCV-123-email-registration
feature/command-palette
bugfix/WCV-204-login-error
release/0.2.0
hotfix/0.2.1
```

规则：

- 只使用 `a-z`、`0-9` 和 `-`
- 不使用空格、下划线、中文和连续连字符
- 有任务编号时使用 `WCV-<编号>-<描述>`
- 没有任务编号时使用简洁的功能描述
- 分支名称不重复写前缀，Git Flow 会自动添加 `feature/`、`release/` 等前缀

## 3. Git Flow 初始化

工具安装：

```bash
brew install git-flow-next
```

本项目使用经典预设：

```bash
git flow init \
  --preset=classic \
  --main=main \
  --develop=develop \
  --shared
```

配置说明：

- 仓库级运行配置存放在 `.git/config`
- 可提交的共享配置存放在根目录 `.gitflow`
- 克隆仓库后执行 `git flow init` 或 `git flow config sync`
- 使用 `git flow config status` 检查本地配置与 `.gitflow` 是否一致

## 4. 功能开发流程

开始功能：

```bash
git switch develop
git pull --ff-only
git flow feature start WCV-123-email-registration
```

开发过程中：

```bash
git add <files>
git commit -m "feat(auth): add email registration"
```

完成任务并合并回 `develop`：

```bash
mvn clean verify
cd workcove-web
pnpm typecheck
pnpm build
cd ..
git flow feature finish WCV-123-email-registration
git push origin develop
```

功能分支必须满足以下条件才能完成：

- 本地构建和测试通过
- 不包含本地数据库密码、Token 或其他敏感信息
- 不混入无关重构
- 已更新相关文档

## 5. 缺陷修复流程

非紧急缺陷使用 `bugfix`：

```bash
git flow bugfix start WCV-204-login-error
git flow bugfix finish WCV-204-login-error
git push origin develop
```

生产环境紧急问题使用 `hotfix`，不要使用普通 `bugfix`：

```bash
git flow hotfix start 0.2.1
```

修复完成后必须补测试，然后执行：

```bash
git flow hotfix finish 0.2.1 -m "Release v0.2.1"
git push origin main develop --tags
```

## 6. 发布流程

版本号遵循语义化版本：

- `MAJOR`：不兼容改动
- `MINOR`：向后兼容的新功能
- `PATCH`：向后兼容的缺陷修复

开始发布：

```bash
git switch develop
git pull --ff-only
git flow release start 0.2.0
```

发布分支只允许：

- 修复发布阻塞问题
- 更新版本号
- 更新发布说明
- 修复构建和文档错误

禁止在发布分支添加新功能。

发布验收：

```bash
mvn clean verify
cd workcove-web
pnpm typecheck
pnpm build
```

完成发布：

```bash
git flow release finish 0.2.0 -m "Release v0.2.0"
git push origin main develop --tags
```

发布完成后确认：

- `main` 包含发布提交
- `develop` 已包含 `main` 的回流
- `v0.2.0` 标签存在
- GitHub Release 使用同一版本号
- 桌面安装包与前端版本号一致

## 7. 提交信息

提交信息使用 Conventional Commits：

```text
<type>(<scope>): <subject>
```

常用类型：

| 类型 | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `refactor` | 不改变行为的重构 |
| `test` | 测试 |
| `docs` | 文档 |
| `build` | 构建、依赖和打包 |
| `chore` | 维护性工作 |

示例：

```text
feat(auth): add email verification code
fix(theme): persist system theme correctly
refactor(identity): separate repository ports
docs(git): add branch workflow
```

## 8. 同步与冲突处理

合并前先同步父分支：

```bash
git flow feature update
```

完成后如果 Git Flow 提示冲突：

1. 手动解决冲突
2. 执行 `git add <files>`
3. 执行 `git flow feature finish --continue`
4. 确认合并结果后再推送

不要用 `git reset --hard` 或强制推送处理共享分支冲突。

## 9. 远程仓库规则

- `main` 和 `develop` 禁止强制推送
- `main` 只接受 `release/*`、`hotfix/*` 或经过验证的合并
- 短期分支完成后及时删除远端分支
- 版本标签使用 `v<major>.<minor>.<patch>`
- 标签创建后禁止移动或覆盖

## 10. 常用查询

```bash
git flow config status
git flow feature list
git flow bugfix list
git flow release list
git flow hotfix list
git status --short --branch
```

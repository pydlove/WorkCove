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

## 质量检查

```bash
mvn clean verify
cd workcove-web && pnpm typecheck && pnpm build
```

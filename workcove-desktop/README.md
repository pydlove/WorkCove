# WorkCove 栖作 Desktop

Electron 只承担 macOS 窗口与后续系统集成，不承载业务逻辑。

开发时先启动 `workcove-web`，再运行：

```bash
pnpm install
pnpm start
```

默认连接 `http://127.0.0.1:32581`，可通过 `WORKCOVE_WEB_URL` 覆盖。

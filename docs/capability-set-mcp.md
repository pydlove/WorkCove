# 能力集更新 MCP

WorkCove Browser MCP 提供 `capability_set_update` 工具，用于把产品能力集网站中的最新能力集文件更新到 `app_audit`。

## 前置条件

1. WorkCove 已登录，并且密码库已解锁。
2. 网址库中存在产品能力集网站。默认按“产品能力集管理系统”“产品能力集”“能力集”匹配，也可以在工具参数中传入 `websiteName`。
3. 网站地址包含产品线，例如：
   `http://172.16.32.34:8087/index.html#/productCapabilityList/maf`
4. 密码库中存在对应账号。默认按网站 origin 匹配条目网址，也可以传入 `credentialName`。
5. `app_audit` 已切换到目标分支，例如 `feature_V3.0.2_R100`。工具会从分支中提取 `V3.0.2_R100`，并匹配能力集中同版本的能力级。

## 使用方式

预览，不修改远端或本地文件：

```text
预览一下当前 app_audit 分支对应的能力集
```

执行更新：

```text
把当前 app_audit 分支对应的能力集更新到 CAPA_CONFIG_AISG.json
```

工具默认执行预览。只有显式传入 `confirm=true` 时才会：

1. 登录能力集网站。
2. 调用页面相同的 `config/save` 保存配置。
3. 等待并下载该能力级最新的 ZIP。
4. 优先提取 `CAPA_CONFIG_AISG.json`，兼容旧名称 `CAPA_CONFIG_MAF.json`。
5. 校验下载内容后，原子覆盖：
   `/Users/panyong/shanjie/app_audit/server/apps/aisg/src/main/resources/capa/CAPA_CONFIG_AISG.json`

## 安全约束

- 密码只从已解锁密码库读取，不进入工具参数、命令行、日志或返回结果。
- 下载的 ZIP 和覆盖前的旧文件保存在 WorkCove 客户端数据目录的 `capability-set-updates` 下。
- 仓库路径必须是 `app_audit` Git 仓库根目录，目标文件路径固定，不能通过工具改写其他文件。
- 能力级版本必须与当前 Git 分支唯一匹配；没有匹配或多个匹配时不会保存或覆盖。

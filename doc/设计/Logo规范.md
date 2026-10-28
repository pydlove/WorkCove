# 8小时工作台 Logo 规范

## 1. 设计方向

Logo 使用 8小时工作台（栖作）的视觉概念：

- 浅色圆角应用底板
- 深石墨数字 8，作为品牌主识别
- 钢蓝色水平基准线，强调稳定与工作节奏
- 青绿色圆点，标记一天中的关键节点
- 克制、清晰、稳定，接近 macOS 原生应用，并保证 24px 下仍可识别

正式设计参考：

```text
doc/设计/WorkCove-logo-reference.png
```

## 2. 正式资产

| 文件 | 用途 |
|---|---|
| `workcove-web/src/assets/brand/workcove-mark.png` | 界面主 Logo |
| `workcove-web/public/brand/workcove-mark-1024.png` | 1024 像素应用图标与分享图 |
| `workcove-web/public/brand/workcove-mark-180.png` | Apple Touch Icon 与启动页 Logo |
| `workcove-web/public/favicon.png` | 浏览器 Favicon |
| `workcove-desktop/assets/icon.png` | Electron 开发窗口和 Dock 图标 |
| `workcove-desktop/assets/WorkCove.icns` | macOS 应用打包图标 |

## 3. 色彩

| 名称 | 色值 | 用途 |
|---|---|---|
| 石墨深蓝 | `#1D272F` | 数字 8 主色与底部轮廓 |
| 钢蓝 | `#5B768A` | 水平基准线 |
| 雾蓝 | `#627B8F` | 数字 8 渐变亮部 |
| 青绿 | `#3BA69E` | 中心节点 |
| 图标底色 | `#FCFDFE` | 应用图标背景 |
| 图标描边 | `#DDE3E8` | 应用图标发丝边界 |

## 4. 使用规则

- 界面最小使用尺寸为 24px
- 图标四周保留不少于图形高度 12% 的安全边距
- 浅色和深色主题都使用浅色应用图标底
- 不拉伸、不旋转、不压缩数字 8 的比例
- 不替换水平基准线和中心节点的品牌色
- 不在图形内部添加产品名、缩写或中文文字
- 不再使用窗口红黄绿状态点、工程网格或装饰灯点
- 数字 8、水平基准线和中心节点组成完整标识，不单独拆分其中一段作为 Logo
- 小尺寸场景使用独立生成的 Favicon，保持数字 8 与中心节点的识别度

## 5. 文字组合

图形右侧使用以下文字组合：

```text
8小时工作台
栖作 · 专注每一天
```

英文字体优先使用 SF Pro Display，其次使用 Inter。中文使用苹方。

文字与图形的间距应不小于图形宽度的 20%。

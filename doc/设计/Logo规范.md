# WorkCove Logo 规范

## 1. 设计方向

Logo 使用 WorkCove 栖作的视觉概念：

- 白灰底色
- 深石墨几何 W
- 左上角三颗 macOS 窗口状态点
- 蓝色与绿色工程辅助线
- 克制、理性、精确，接近 macOS 原生应用

原始设计参考：

```text
doc/设计/WorkCove-logo-reference.png
```

## 2. 正式资产

| 文件 | 用途 |
|---|---|
| `workcove-web/src/assets/brand/workcove-mark.svg` | 界面主 Logo，可无损缩放 |
| `workcove-web/public/brand/workcove-mark-1024.png` | 1024 像素应用图标与分享图 |
| `workcove-web/public/favicon.svg` | 浏览器 Favicon，针对小尺寸简化 |
| `workcove-desktop/assets/icon.png` | Electron 开发窗口和 Dock 图标 |
| `workcove-desktop/assets/WorkCove.icns` | macOS 应用打包图标 |

## 3. 色彩

| 名称 | 色值 | 用途 |
|---|---|---|
| 石墨深蓝 | `#0A1728` | W 主色 |
| 钢蓝 | `#243B55` | W 亮部 |
| 工程蓝 | `#76A9E8` | 结构辅助线 |
| 工程绿 | `#16C878` | 对齐辅助线 |
| 窗口红 | `#FF5B54` | 左上控制点 |
| 窗口黄 | `#FFB53B` | 左上控制点 |
| 窗口绿 | `#2BCC3F` | 左上控制点 |
| 图标底色 | `#F8FAFD` | 应用图标背景 |

## 4. 使用规则

- 界面最小使用尺寸为 24px
- 图标四周保留不少于图形高度 12% 的安全边距
- 浅色和深色主题都使用白色应用图标底
- 不拉伸、不旋转、不压缩 W 的比例
- 不替换红黄绿三颗状态点的颜色
- 不在图形内部添加 WorkCove、WCV 或中文文字
- 小尺寸场景使用简化 Favicon，不直接缩小白底工程辅助线
- 工程辅助线只能作为低对比度细节，不能盖过 W

## 5. 文字组合

图形右侧使用以下文字组合：

```text
WorkCove
栖作 · PERSONAL WORKSPACE
```

英文字体优先使用 SF Pro Display，其次使用 Inter。中文使用苹方。

文字与图形的间距应不小于图形宽度的 20%。

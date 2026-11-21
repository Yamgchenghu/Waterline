# 流水入杯

一个用原生 Android Java 编写的 2D 水流物理解谜游戏。画线、搭弧、推动杯子、利用火焰与机关，把水引进杯子并装满 95%。

> **下载 APK**：见 [Releases](https://github.com/Yamgchenghu/Waterline/releases/latest)。

## 游戏内容

- 16 个正式关卡，按顺序解锁
- 内置 1–16 关官方通关教程，可模拟播放
- 达到 95% 杯量即通关
- 水珠、线、杯子、弧线、机关均为实时物理模拟
- 完成至少 10 关后解锁关卡编辑器

## 机关

- 挡板、火线、弧线挡板、弧线火线
- 弹性挡板、单向阀、单向弧、可破坏木板
- 固定圆、火环、火球、泡泡水球、毒水球、动态圆球、冰砖
- 毒水龙头、毒水净化器
- 支撑挡板（已合并为白色弧挡板）
- 可移动机关：来回、多段路径、绕圈、原地旋转、闪烁
- 旋转触发器：附着在无物理机关上，液体碰到后按角度和角速度旋转

## 编辑器

- 添加分类：直线 / 弧线 / 圆 / 文字
- 手绘直线和弧线
- 整体移动 / 仅端点移动
- 圆弧三点、直线两点、圆中心和半径控制
- 多段可视路径，可设置往复/循环和旋转
- 闪烁运动：初始显示/隐藏、显示/隐藏时长、隐藏时是否碰撞
- 文字富文本：`<color rrrgggbbb>`、`<color rrrgggbbbaaa>`、`<bold>`
- 文字输入记忆上次内容，支持 `<`、`>`、color、bold 快捷插入
- `.flowlevel` JSON 导入/导出
- 自定义关卡可试玩并录制教程

## 物理规则摘要

- 世界坐标 0.60 × 1.00，单位米/千克/秒
- 水珠物理为正常值的 2/3，杯子体积为正常值的 7/17
- 水珠必须接触杯内水面才计数；空杯毒水不会消失
- 所有物体静止 5 秒、杯子离屏 5 秒、手动结束 5 秒、达到 95% 后 15 秒结算
- 毒水入杯扣水，碰水生成泥珠
- 火线/火环/火球蒸发水珠
- 可破坏木板：火焰点燃后向两端 1cm/s 蔓延，1 秒后熔断；撞击按法向分力判定
- 毒水龙头初始向下，并带短暂 ±0.15N 横向力

## 构建

项目不使用 Gradle，构建脚本直接调用 Android SDK 工具：

```bash
./build.sh
```

依赖：

- JDK 11+
- Android SDK Build-Tools（`aapt2`、`d8`、`apksigner`）
- Android 34 平台 `android.jar`
- Python 3

默认 SDK 路径可在 `build.sh` 中通过 `ANDROID_SDK` 修改。签名密钥在 `tools/waterline.keystore`，仅供本地测试构建；正式发布请使用自己的签名。

产物：

```text
dist/流水入杯-正式版-v1.0.40.apk
```

## 目录

```text
android/
  src/                  Java 源码
  res/                  图标、主题、字符串
  assets/levels/        16 个关卡 JSON
  assets/tutorials.json 官方教程存档
  assets/fire_ring.png  火环贴图
build.sh                构建、打包、签名
tools/package_apk.py    APK 打包辅助脚本
```

## 许可

代码与关卡采用 [MIT License](LICENSE)。

## 致谢

使用原生 Canvas 与纯 Java 2D 物理实现，不依赖游戏引擎。

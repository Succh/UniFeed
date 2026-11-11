# v16 应龙启动图标落地说明

对应源图：`docs/design/app-icon-dragon/muse-v16-final.webp`（1600×1600，青绿应龙 + RSS 龙珠）。
本说明只记录 v16 的落地；1 号小龙方案的文件保持原样。

## 落地内容（分支 opt/app-icon-dragon）

- 自适应图标（108dp 层）：
  - 前景 `mipmap-*/ic_launcher_foreground.png`（mdpi 108 / hdpi 162 / xhdpi 216 / xxhdpi 324 / xxxhdpi 432）
  - 背景 `drawable/ic_launcher_background.xml`：径向渐变，中心 (58%, 42%)，半径 78dp，
    `#04AAA4 → #00787C → #00383E`（按源图纯背景像素实测取色）
  - 单色层 `mipmap-*/ic_launcher_monochrome.png`（Android 13+ themed icons，alpha 剪影）
  - `mipmap-anydpi-v26/ic_launcher{,_round}.xml` 指向 `@mipmap` 前景/单色 + `@drawable` 背景
- legacy 全密度：`mipmap-*/ic_launcher.png`（全出血渐变方图）与 `ic_launcher_round.png`（圆形裁切），
  构图沿用源卡片比例（龙占高 ~92%，双足踩底框线）
- `values/colors.xml` 的 `ic_launcher_background` 同步为 `#00555C`（themes.xml 的 windowBackground 引用它，
  启动背景与图标衔接；`primary`/`accent` 等 UI 色未动）

## 安全区与放置

- 前景整龙 bbox（源图 147..1472 × 51..1508）缩放 0.56、以画布中心居中：
  - 龙头（含角）+ RSS 龙珠整体位于中心 66dp 安全区内（左角尖距圆心 ~32dp < 33dp）
  - 五爪双足落在 72dp 可见圆内，圆/方两种 mask 均可见
- 底框线以下（y ≥ 1507 源坐标）整体裁平，与源图"身子弯到下框即止、双足扒底框"一致

## 去底方法与效果自查

- 纯 PIL 两阶段泛洪分割（无 numpy/rembg 环境）：先以低纹理阈值（局部对比 <9）从卡片内缘种子保守泛洪，
  再放宽（<30，即强墨线为界）越过龙身周围的柔和暗晕；墨线轮廓为屏障，龙身完整保留
- 清理：底框硬切、边缘 6px 带、圆角矩形内裁、四角暗晕楔（颜色+贴边规则，龙爪因墨线+浅色不受影响）、
  连通域面积/贴背景率过滤
- 自查：龙须左右卷曲完整、鬃毛边缘干净无底色镶边；龙珠圆形完整、RSS 白色纹样清晰；
  棋盘格目视无漂浮残点（checker 预览与 66/72dp 安全区叠图均已人工核验）

## 构建验证

- `./gradlew assembleDebug`：BUILD SUCCESSFUL（JDK 17 + ANDROID_HOME=/data/home/android-sdk）
- APK 内已确认打包全部层与密度（res/mipmap-*-v4/ic_launcher{,_foreground,_monochrome,_round}）

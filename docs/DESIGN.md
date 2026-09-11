# Narvive 视觉设计规范（DESIGN.md）

> 版本：v1.0 · 2026-08-19
> 定位：Narvive = Narrative + Alive —— 让小说角色"活起来"的 AI 阅读器。
> 本规范是全站视觉重设计的唯一基准（Single Source of Truth）。所有 UI 改动必须以此为依据；实施过程只允许视觉层改动，严禁触碰 data / domain / service 业务逻辑。

---

## 0. 双表面架构（最重要的前提）

Narvive 的界面分为两个"表面"，各自独立、互不污染：

| 表面 | 范围 | 控制方式 |
| --- | --- | --- |
| **Chrome 面（外壳）** | 书架 / 笔记 / AI / 统计 / 设置 / 全部面板与对话框 | 外观主题（本规范新增）× 外观模式（浅色/深色/跟随系统，已有） |
| **阅读面（正文）** | 阅读器正文区域 | 阅读主题 5 套纸色（纸白/羊皮纸/豆沙绿/墨夜/纯黑 + 自定义），用户阅读时独立切换，已有 `LocalReadingTheme` 机制，**本次不重做，仅微调色值** |

阅读器的 HUD（顶栏/底栏/设置面板）跟随 Chrome 面；正文区域永远跟随阅读面。

---

## 1. 美学风格定义

**设计方向：「暖纸墨 · 活字」（Warm Paper & Living Ink）**

一句话：像一本装帧精良的书——温暖、克制、有文学气质；AI 出现时，墨色里透出一点"活"的温度。

- **骨**：文学编辑排版。全站统一使用 HarmonyOS Sans（无衬线），靠字号梯度与字重分层，形成克制、清晰、现代的阅读气质。
- **肉**：温暖纸感。所有中性色带黄棕底色，拒绝冷蓝灰；边界用"耳语级"细线（1dp、低透明度）而非重边框。
- **魂**：赤陶橙是唯一的强色彩，代表"生命力"，集中出现在主按钮、选中态、AI 相关入口与品牌时刻。
- **参考系**：Claude 的文学沙龙气质（暖纸 + 赤陶 + 衬线）× Notion 的轻边界与多层微阴影。

**反模式（禁止）**：冷蓝灰大底、高饱和多彩渐变滥用、玻璃拟态堆叠、重投影、锐利直角卡片、正文区域出现品牌色干扰阅读。

---

## 2. 外观主题体系（Chrome 面）

外观主题 = 色彩气质；外观模式 = 明暗。两者正交组合：每个主题都有浅色 / 深色两套 ColorScheme。

| id | 名称 | 气质 | 说明 |
| --- | --- | --- | --- |
| `paper_ink` | 暖纸墨 | 羊皮纸底 + 赤陶橙 | 品牌主方向，文学感最强 |
| `sky` | 天际蓝（默认） | 冷灰 + 天蓝 | 1.0 经典外观；当前为默认主题 |
| `pine` | 松烟绿 | 灰绿纸底 + 松绿 | 沉静护眼，东方书房感 |
| `plum` | 绛紫 | 暖灰紫底 + 绛紫 | 夜色浪漫，适合睡前阅读场景 |

### 2.1 暖纸墨 paper_ink

> 注：`paper_ink` 是品牌主方向，但**应用默认主题已改为 `sky`（天际蓝）**，
> 见 `AppearanceThemes.DEFAULT_ID`。本节色值不受影响。

**浅色**

| 角色 | 色值 | 用途 |
| --- | --- | --- |
| background | `#F7F4EE` 羊皮纸 | 页面大底 |
| surface | `#FCFAF5` 象牙白 | 卡片/栏位 |
| surfaceVariant | `#F0ECE2` 暖沙 | 次级容器/选中底 |
| primary | `#C4553B` 赤陶 | 主按钮/选中/品牌 |
| onPrimary | `#FFFFFF` | |
| primaryContainer | `#F6DDD2` 浅陶 | 图标底/Chip 底 |
| onBackground | `#2B2620` 暖墨 | 主文本（非纯黑） |
| onSurfaceVariant | `#6E6455` 橄榄灰 | 次级文本 |
| outline | `#E3DCCD` 奶油边 | 细边界 |
| error | `#B44336` | 错误 |

**深色**

| 角色 | 色值 | 用途 |
| --- | --- | --- |
| background | `#1B1815` 暖墨夜 | |
| surface | `#262119` 暖炭 | |
| surfaceVariant | `#322B22` | |
| primary | `#E0815F` 珊瑚陶 | 暗色下提亮的品牌色 |
| primaryContainer | `#5A3125` | |
| onBackground | `#EDE6DA` 暖银 | |
| onSurfaceVariant | `#B9AC99` | |
| outline | `#4A4034` | |
| error | `#E08A7E` | |

### 2.2 天际蓝 sky（经典，原样保留）

浅色：primary `#0284C7`、background `#F8FAFC`、surface `#FFFFFF`、surfaceVariant `#F1F5F9`、outline `#E2E8F0`、onBackground `#0F172A`、onSurfaceVariant `#64748B`、error `#DC2626`。
深色：primary `#38BDF8`、background `#0F172A`、surface `#1E293B`、surfaceVariant `#334155`、outline `#475569`、onBackground `#E2E8F0`、onSurfaceVariant `#94A3B8`、error `#F87171`。

### 2.3 松烟绿 pine

浅色：background `#F4F6F1`、surface `#FBFCF9`、surfaceVariant `#E8EDE3`、primary `#3F6B4F`、primaryContainer `#DCE9DE`、onBackground `#21271F`、onSurfaceVariant `#5A675A`、outline `#DDE4D6`。
深色：background `#151A16`、surface `#1E2620`、surfaceVariant `#293229`、primary `#8FBE9C`、primaryContainer `#2C4A38`、onBackground `#E4EAE0`、onSurfaceVariant `#A8B5A4`、outline `#3E4A3E`。

### 2.4 绛紫 plum

浅色：background `#F8F5F7`、surface `#FDFBFC`、surfaceVariant `#F0E9EE`、primary `#7A4A6B`、primaryContainer `#F2DFE9`、onBackground `#282126`、onSurfaceVariant `#6B5A64`、outline `#E5DAE1`。
深色：background `#1B151A`、surface `#251D24`、surfaceVariant `#322832`、primary `#D8A7C4`、primaryContainer `#553249`、onBackground `#EEE5EC`、onSurfaceVariant `#C2AFBC`、outline `#4A3D47`。

### 2.5 语义色（全主题统一）

成功 `#16A34A`（浅色）/ `#4ADE80`（深色）；警告 `#D97706` / `#FBBF24`；错误随主题（上表）。
高亮四色（阅读标注，不动）：黄 `#FACC15`、蓝 `#38BDF8`、粉 `#F472B6`、绿 `#4ADE80`。
标注类型色（高亮/笔记/翻译/AI 保存/角色对话/改写）为**固定语义色板、主题与明暗无关**，已收拢到 `ui/theme/SemanticColor.kt` 的 `AnnotationPalette` + `annotationTypeColor(type)`，替代 NotesScreen / BookDetailsScreen / TocBottomSheet 的重复硬编码。

---

## 3. 布局与信息架构

- **8dp 网格**：所有间距落在间距 Token 上（见 §5），页面左右边距统一 20dp（卡片内 14–16dp）。
- **页面骨架**：Tab 页统一 `TabPageScaffold`；二级页统一"返回 + 标题"顶栏；面板统一 BottomSheet 语言（圆角 24dp、顶部拖拽柄）。
- **信息层级**（以书架卡片为例）：封面 → 书名（最多 2 行）→ 进度/元数据 → 操作。进度环等图形元素只在详情/统计承担主视觉，列表中降级为细进度条。
- **视觉动线**：每屏一个主行动点（primary），其余为次级（表面色/描边）；同屏 primary 不超过 1 个。
- **导航结构不动**：5 Tab + 现有 20 条路由全部保留，本次不做信息架构层面的增删。

---

## 4. 排版规范

**统一无衬线字族**（用户确认：全站不使用衬线）：

| 字族 | 用途 | 实现 |
| --- | --- | --- |
| HarmonyOS Sans SC | 全站 UI（标题/正文/按钮/标签/设置） | 打包 Regular(400)/Medium(500)/Bold(700) 到 `res/font/`，SemiBold(600) 由 Compose 就近匹配；许可证见 `res/raw/harmonyos_sans_license.txt` |

> 阅读正文（EPUB/TXT/PDF）不走此字族，沿用 `service/font` 的独立字体系统；两者互不影响。

**字号梯度（10 档，替代当前 150+ 处散写）**：

| Token | sp | 字重 | 行高 | 字距 | 典型用途 |
| --- | --- | --- | --- | --- | --- |
| headlineLarge | 28 | Bold | 36 | -0.4 | 页面主标题 |
| headlineMedium | 24 | Bold | 32 | -0.2 | 区块标题 |
| headlineSmall | 20 | Bold | 28 | 0 | 卡片大标题 |
| titleLarge | 18 | SemiBold | 26 | 0 | 列表主标题/书名 |
| titleMedium | 16 | Medium | 24 | 0 | 强调正文/按钮 |
| titleSmall | 15 | Medium | 22 | 0 | 紧凑列表标题 |
| bodyLarge | 15 | Normal | 24 | 0 | 主要正文 |
| bodyMedium | 14 | Normal | 20 | 0 | 次级正文 |
| bodySmall | 13 | Normal | 18 | 0 | 辅助说明 |
| labelMedium | 12 | Medium | 16 | 0.2 | 标签/元数据 |
| labelSmall | 11 | Medium | 15 | 0.4 | 胶囊/角标（不再使用 10sp） |

规则：字重只用 400/500/700 三档；行比 = 行高 ÷ 字号 ∈ [1.2, 1.6]；正文行比不低于 1.4；Chip 文字不换行、最小 11sp。

---

## 5. 间距 Token（8dp 网格）

`2 / 4 / 6 / 8 / 10 / 12 / 16 / 20 / 24 / 32 / 40`，命名：

| Token | dp | 用途 |
| --- | --- | --- |
| xxxs / xxs / xs | 2 / 4 / 6 | 图标与文字间隙、内边距微调 |
| sm / smd / md | 8 / 10 / 12 | 组件内部间距、Chip 间距 |
| lg / xl | 16 / 20 | 卡片内边距、页面左右边距（20） |
| xxl / xxxl / huge | 24 / 32 / 40 | 分组间距、区块间距、屏级呼吸位 |

---

## 6. 图标体系

- **来源**：Material Symbols **Rounded**（项目已依赖 material-icons-extended，统一使用 `Icons.Rounded.*`，禁止混用 Filled/Outlined/Sharp）。
- **尺寸两档**：20dp（列表行内/Chip）、24dp（导航/工具栏）；装饰性大图标 28/48dp 例外。视觉重量统一：同排图标同尺寸同透明度。
- **触控目标**：可点图标热区 ≥ 48dp（用 `IconButton` 或 `minimumInteractiveComponentSize`）。
- **图标容器**：设置类列表沿用 32dp 圆角方块 + primaryContainer 底 + primary 图标（18dp）的既有语言，推广到全站。
- 自定义图标（如自动翻页扫描线、墨滴母题）后续以 ImageVector 补充，风格对齐 Rounded（2dp 圆头描边）。

---

## 7. 质感与材质

- **主材质**：哑光纸面。浅色下 background→surface 的明度差 ≤ 3%，靠细边界与微阴影分层，不靠强对比。
- **渐变**：仅允许三处——① 角色头像（RoleplayAvatar 双色系渐变，色值收编进主题）；② AI 氛围点缀（全局 AI 页问候卡，主色 8%→0% 透明）；③ 统计图表柱体（主色→主色 60%）。其余场景禁用渐变。
- **模糊**：不使用背景模糊（Android 12- 性能差）；需要分层时用表面色 + 微阴影。
- **图片/封面**：书封统一 8dp 圆角 + 1dp outline 细边（防白封融入纸底）；`BookCoverFallback` 渐变底色跟随主题。
- **阅读面**：5 套纸色微调（见 §0），仅校正墨色对比度至 ≥ 7:1（WCAG AAA），不改机制。

---

## 8. 阴影与层级（海拔）

| 层级 | 名称 | 表现 | 用途 |
| --- | --- | --- | --- |
| L0 | 平面 | 无阴影无边框 | 页面大底 |
| L1 | 细边 | 1dp outline 50% 透明 | 卡片、输入框、列表分组 |
| L2 | 浮起 | shadowElevation 2dp（ambient 6%） | 选中卡片、悬浮按钮 |
| L3 | 弹层 | 6dp + 表面色 | BottomSheet、菜单、HUD |
| L4 | 模态 | 12dp + scrim 32% | 对话框、全屏弹层 |

原则：浅色主题"以边代影"（L1 为主）；深色主题禁用投影，用表面明度差 + 细边分层。Compose 侧以 `NarviveElevation` Token 提供，禁止散写 `shadow(N.dp)`。

---

## 9. 圆角规范

| Token | dp | 用途 |
| --- | --- | --- |
| shapeXs | 4 | 小标签、进度条 |
| shapeSm | 8 | 书封、小按钮、图标容器 |
| shapeMd | 12 | 输入框、小卡片、列表项 |
| shapeLg | 16 | 标准卡片、对话框内容区 |
| shapeXl | 24 | BottomSheet 顶角、特色卡片 |
| shapePill | 50% | Chip、胶囊按钮、头像 |

规则：同组元素圆角一致；嵌套时外角 ≥ 内角（外 16 内 12）；书封全站固定 8dp。

---

## 10. 动效设计

**时长三档**（`NarviveMotion`）：

| Token | ms | 用途 |
| --- | --- | --- |
| instant | 80 | 按压反馈 |
| fast | 150 | 透明度/选中态切换 |
| medium | 250 | 组件级过渡（面板展开、Tab 切换） |
| slow | 400 | 页面转场、共享元素 |

**缓动**：
- 标准 Standard `CubicBezier(0.2, 0.0, 0.0, 1.0)` —— 默认；
- 强调减速 EmphasizedDecelerate `CubicBezier(0.05, 0.7, 0.1, 1.0)` —— 入场；
- 强调加速 EmphasizedAccelerate `CubicBezier(0.3, 0.0, 0.8, 0.15)` —— 退场。

**页面转场**：Tab 切换 fadeThrough（淡出 150 + 淡入 250）；二级页 push/pop 用 slide + fade（400ms）；打开书籍保留现有共享元素/展开动画（`BookOpenOverlay`），参数收编进 Token。
**阅读翻页**：跟随现有 `page_flip_animation` 设置，不改。

---

## 11. 微交互

- **按压**：按钮/卡片 `scale(0.97)` + 默认 ripple（主色 12%）；80ms 内完成。
- **选中**：Chip/Tab 选中用主色 12% 底 + 主色文字，过渡 150ms；禁止跳变。
- **加载**：统一 `EightSegmentLoading`（收编进组件库）；列表加载用骨架屏（表面色呼吸 1000ms 循环）；AI 流式输出沿用 `StreamingCursor`。
- **HUD**：阅读器 HUD 进出 fade + 位移 8dp，250ms；悬浮元素不得遮挡正文（沿用现有 inset 处理）。
- **翻页/拖动反馈**：进度条拖动、图表缩放的跟手动画用弹簧（stiffness 中档），不指定时长的物理动画优先。

---

## 12. 状态反馈

| 状态 | 规范 |
| --- | --- |
| 默认 | 本规范 Token |
| 按压 | §11 scale + ripple |
| 禁用 | onSurface 38% 透明度，表面色 12%；禁止仅降透明度导致主按钮看似可点 |
| 加载 | §11 骨架/指示器；按钮加载 = 内联 spinner 替换文字，宽度不变 |
| 成功 | 成功绿（§2.5）图标 + 文案，Snackbar 停留 ≤ 2s |
| 错误 | 主题 error 色；表单错误 = error 描边 + 图标配文；AI 错误沿用现有可读错误文案 |
| 空状态 | 统一三段式：线性插画/大图标（48dp，onSurfaceVariant 40%）+ 一句标题（titleMedium）+ 一句引导（bodyMedium）+ 可选主按钮 |

---

## 13. 形态语言

- **母题：「墨滴」圆**。圆形元素贯穿：阅读进度环、角色头像、选中态圆点、AI 入口。圆代表"角色活了"的生命感。
- **双线风格**：衬线标题 + 无衬线功能的张力贯穿每个页面头部。
- **纸的层叠**：所有浮层是"纸上的纸"——同色系的更亮一层，而非异色块。
- **AI 的存在感**：AI 功能入口统一 `AutoAwesome` 图标 + 主色点缀；AI 生成内容卡片左侧 2dp 主色竖线标识，与用户内容区分。

---

## 14. 实施路线（纯视觉，零逻辑改动）

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| S0 ✅ | 本规范 + 评审 | 用户确认 |
| S1 | 主题层 Token：`AppearanceTheme`（4 主题 × 浅深）、Shape/Spacing/Elevation/Motion、Type 扩展；DataStore 新增 `appearance_theme` 键；设置-外观页新增主题选择器 | 编译通过，外观页可切换主题 |
| S2 | 逐屏收编硬编码（顺序：设置 → 书架/详情 → 笔记/统计 → AI 对话/角色扮演/改写 → 阅读器 HUD），每屏替换 Color/sp/RoundedCornerShape 为 Token | 每屏编译通过 + 目视走查 |
| S3 | 组件统一：按钮/卡片/Chip/空状态/加载收进 `ui/components` | 全站一致 |
| S4 | 动效与微交互精调、HarmonyOS Sans 打包（已完成）、自定义图标补充 | 走查清单全绿 |

**安全约定**：不改 data/domain/service 任何行为；DataStore 只新增键；`LocalReadingTheme` 机制不动；每阶段 `gradlew :app:compileDebugKotlin` 验证。

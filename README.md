# NotePad

基于 AOSP NotePad 示例改造的 Android 记事本应用。在原示例的 ContentProvider + SQLite 架构上，完成了「笔记时间戳显示」「按标题或内容搜索」两项基本功能，并扩展了「笔记分类」「日间/夜间主题」两项功能。

- 语言：Java（Java 17）
- 构建：AGP 8.12.2 / Gradle 8.13
- 版本：compileSdk 36、targetSdk 36、minSdk 21、Build Tools 36.0.0

## 功能一览

| 类型 | 功能 | 实现要点 |
| --- | --- | --- |
| 基本功能 | 笔记列表显示时间戳 | 在 `SimpleCursorAdapter` 的 `ViewBinder` 中读取 `created` 列，格式化为 `创建于 yyyy-MM-dd HH:mm` |
| 基本功能 | 按标题或内容搜索 | Toolbar 内嵌 `SearchView`，实时把关键字拼成 `title LIKE ? OR note LIKE ?` 查询 provider |
| 扩展功能 | 笔记分类 | 编辑器用 `Spinner` 选择分类；列表用 `ChipGroup` 筛选；数据库升级新增 `category` 列 |
| 扩展功能 | 日间/夜间主题 | `AppCompatDelegate.setDefaultNightMode` 切换，`SharedPreferences` 持久化，`values-night` 提供深色配色 |

其他细节：FAB 新建笔记、长按条目弹出「打开 / 复制 / 删除」菜单、剪贴板内容一键粘贴为新笔记、空列表提示。

## 界面说明

### 笔记列表

- 顶部为 MaterialToolbar，标题为应用名，菜单包含搜索、新建、主题切换、粘贴。
- 工具栏下方是一排分类 Chip：全部 / 工作 / 学习 / 生活 / 其他，单选，切换后立即刷新列表。
- 列表项为圆角卡片，自上而下依次显示：加粗标题 + 右侧分类徽标、一行内容摘要、灰色时间戳。
- 右下角 FAB 用于新建笔记；列表为空时居中显示提示文案。

### 笔记编辑

- 顶部工具栏，下方一行「分类」选择器，再往下是带横线的编辑区域。
- 编辑区使用自定义 `LinedEditText`，按当前主题在文字基线下方绘制浅色横线；横线颜色随夜间模式变化。
- 工具栏菜单支持保存与删除；从剪贴板粘贴时会自动填入标题与正文。

## 技术实现

### 1. 数据层：ContentProvider + SQLite

- `NotePad.java`：契约类，定义 authority、`content://` URI、MIME 类型、列名与默认排序（`modified DESC`）。
- `NotePadProvider.java`：继承 `ContentProvider`，用 `UriMatcher` 区分集合与单条 URI，用 `SQLiteQueryBuilder` + projection map 执行查询，增删改后调用 `notifyChange` 通知 UI 刷新。
- 数据库 `note_pad.db`，表 `notes`：`_id`、`title`、`note`、`created`、`modified`、`category`。

### 2. 数据库升级保留旧数据

数据库版本升至 3，新增 `category` 列。`onUpgrade` 没有采用示例里的「删表重建」，而是增量迁移：

```java
if (oldVersion < 3) {
    db.execSQL("ALTER TABLE notes ADD COLUMN category TEXT DEFAULT '其他'");
    oldVersion = 3;
}
```

同时在建表语句中为 `category` 设置默认值，`insert()` 时若调用方未传分类也会补上默认值，保证旧数据与新数据行为一致。

### 3. 时间戳显示

列表查询的 projection 增加 `created` 列，通过 `ViewBinder` 把毫秒时间戳格式化为可读文本，并对内容摘要做换行替换与空值隐藏处理，避免卡片出现空白行。

### 4. 搜索

搜索逻辑集中在 `refreshList()`：根据当前关键字与分类动态拼接 `selection` 与 `selectionArgs`，关键字使用 `%keyword%` 占位符（不拼接字符串），并给「标题或内容」条件加括号，保证与分类条件是 `AND` 关系。

```java
selection.append("(title LIKE ? OR note LIKE ?)");
```

`SearchView` 的 `onQueryTextChange` 直接触发刷新，做到边输入边过滤。

### 5. 分类筛选

- 分类候选值统一放在 `res/values/arrays.xml`，编辑器的 `Spinner` 与契约类的默认值保持一致。
- 列表页 `ChipGroup` 单选，选中「全部」时该条件不参与查询，其余分类作为 `category = ?` 条件。

### 6. 日间/夜间主题

- 颜色按语义命名（`background`、`surface`、`on_surface`、`line`、`badge_bg` 等），`values/colors.xml` 与 `values-night/colors.xml` 各提供一套。
- 主题继承 `Theme.MaterialComponents.DayNight.NoActionBar`，切换时只改 `AppCompatDelegate` 的模式，不重建颜色配置。
- 选择结果写入 `SharedPreferences`，并在 `NotePadApp`（Application）的 `onCreate` 中恢复，避免冷启动闪回浅色。
- 自定义控件的绘制颜色单独适配，例如编辑区横线在夜间使用浅色、日间使用深色。

## 项目结构

```text
NotePad/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/android/notepad/
│       │   ├── NotePad.java          # 契约类：URI、列名、MIME
│       │   ├── NotePadProvider.java  # ContentProvider + DatabaseHelper
│       │   ├── NotesList.java        # 列表页：时间戳、搜索、分类筛选、主题切换
│       │   ├── NoteEditor.java       # 编辑页：分类选择、带横线编辑区
│       │   ├── TitleEditor.java      # 标题编辑
│       │   └── NotePadApp.java       # 恢复主题设置
│       └── res/
│           ├── layout/               # 列表、列表项、编辑页布局
│           ├── menu/                 # 列表与编辑页菜单
│           ├── drawable/             # 卡片、分类徽标、添加图标
│           ├── values/               # 浅色配色、主题、分类数组
│           └── values-night/         # 深色配色
├── build.gradle
├── gradle.properties
└── settings.gradle
```

## 构建与运行

环境要求：JDK 17、Android SDK Platform 36、Build Tools 36.0.0。

```bash
# 编译 Debug 包
./gradlew assembleDebug

# 产物位置
app/build/outputs/apk/debug/app-debug.apk
```

说明：

- 若工程路径包含中文，需在 `gradle.properties` 中保留 `android.overridePathCheck=true`。
- 国内网络环境可在 Gradle 中配置阿里云镜像加速依赖下载。

## 界面预览

建议补充以下截图（放入 `docs/screenshots/` 后在本节以图片形式展示）：

| 文件名 | 内容 |
| --- | --- |
| `list_day.png` | 笔记列表（浅色主题，含时间戳、分类徽标） |
| `list_night.png` | 笔记列表（深色主题） |
| `search.png` | 搜索关键字后的过滤结果 |
| `category.png` | 分类 Chip 筛选效果 |
| `editor.png` | 编辑页（分类选择 + 带横线编辑区） |

## 参考

- AOSP NotePad 示例
- [Android Sample--NotePad 解析](https://blog.csdn.net/llfjfz/article/details/67638499)
- ContentProvider 与 SQLite 数据层源码精读：<https://blog.csdn.net/2402_88517277/article/details/171301902>
- 深色主题适配实战：<https://blog.csdn.net/2402_88517277/article/details/171437608>

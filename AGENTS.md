# Pixiv-MultiPlatform-2 项目规范

本项目的详细开发能力拆分在仓库内的 `.agents/skills/pmf-*` 中。技能说明使用简体中文；业务日志字符串遵循 `pmf-logger` 的英文要求。

## 项目范围技能

- 只使用 `.agents/skills/pmf-*` 下的项目技能，不把项目规范复制到全局技能目录。
- 每个任务先加载 `pmf-workflow`，由它调研用户意图、确认代码边界并选择其他技能。
- 技能缺失时先报告，不用全局同名技能替代，也不要在任务中临时创建未命名的项目规范。
- 详细规则只维护在对应技能中，避免本文件与技能内容漂移。

## 技能加载条件

| 用户需求或代码信号                                                  | 必须加载的技能                                           |
|------------------------------------------------------------|---------------------------------------------------|
| 用户可见文本、`sharedUI/i18n`、YAML、`Lang.string`、`stringResource` | `pmf-i18n`                                        |
| `route.kt`、`model.kt`、`screen.kt`、Nav3、路由、页面状态或一次性 effect  | `pmf-page`                                        |
| 修改或设计 `screen.kt`、`model.kt`、ViewModel 状态/effect           | `pmf-page` + `pmf-logger`；含 UI 布局时再加 `pmf-design` |
| `@Composable`、私有 Content、加载/错误/刷新、动画、Preview、组件复用          | `pmf-design`                                      |
| Lazy 列表/网格、Paging、瀑布流、滚动状态、滚动条、稳定 key、占位内容                 | `pmf-list`                                        |
| Dialog、Bottom Sheet、Drawer、选择器或蒙层流程                        | `pmf-overlay`；对话框路由同时加载 `pmf-page`                |
| ViewModel、Repository、网络、缓存、持久化、异常或 fallback                | `pmf-logger`                                      |

## 必须遵守的项目边界

1. 模糊的代码上下文先使用 fast-context 语义搜索，并传入真实仓库根目录；语义结果只能作为候选，必须使用 `rg` 和源码调用链确认。
2. 修改 `screen.kt` 或 `model.kt` 时必须加载 `pmf-logger`。UI 层状态、挂载、分页、滚动和布局追踪必须使用 `sharedUI/src/commonMain/kotlin/top/kagg886/pmf/util/logger.kt` 中的 `TraceEffect`。
3. 用户只要求结论、调研或方案时不修改工作区；保留无关的未提交修改。
4. 所有用户可见文本、页面结构、Compose 设计、列表分页、弹层和日志规则分别以对应 `pmf-*` 技能为准。

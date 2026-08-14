---
name: pmf-workflow
description: 分析 Pixiv-MultiPlatform-2 的开发需求、定位真实代码边界并按文件与意图加载对应的 pmf 项目技能。用于任何需要理解、诊断、设计或修改本项目代码的任务。
---

# PMF 任务编排

## 目标

先理解用户目标和实际代码边界，再选择最小但完整的项目技能集合。不要仅根据文件名猜测架构，也不要把项目技能安装到全局目录。

## 执行流程

1. 判断任务是调研、诊断、设计还是修改。用户只要结论或方案时，不修改工作区。
2. 如果文件、模块或调用链不明确，使用 `mcp__fast_context__fast_context_search`，传入仓库根目录 `C:\Users\iveou\IdeaProjects\Pixiv-MultiPlatform-2`；把语义搜索结果当作候选，再用 `rg`、文件读取和真实调用链确认。
3. 根据用户意图、文件路径和关键词加载下表中的技能。一个任务可以同时加载多个技能。
4. 修改前说明命中的技能边界；发现必需技能缺失时先报告，不用全局同名技能替代项目技能。
5. 保留与任务无关的未提交修改，只修改用户要求的文件。

## 技能加载矩阵

- 用户可见文本、`sharedUI/i18n`、YAML、`Lang.string` 或 `stringResource`：加载 `pmf-i18n`。
- `route.kt`、`model.kt`、`screen.kt`、Nav3、路由、状态或一次性 effect：加载 `pmf-page`。
- 修改或设计 `screen.kt`、`model.kt`、ViewModel 状态/effect：强制同时加载 `pmf-logger`；如果包含布局或组件，再加载 `pmf-design`。
- `@Composable`、私有 `Content`、加载/成功/错误/刷新状态、过渡动画、Preview 或组件复用：加载 `pmf-design`。
- Lazy 列表/网格、Paging、滚动状态、滚动条、稳定 key、占位内容或瀑布流：加载 `pmf-list`。
- Dialog、Bottom Sheet、Drawer 或需要蒙层的流程：加载 `pmf-overlay`；对话框路由同时加载 `pmf-page`。
- ViewModel、Repository、请求、缓存、持久化、异常或 fallback：加载 `pmf-logger`。

## 边界

- 详细项目规范只放在对应的 `pmf-*` 技能中；不要在本技能中复制完整 UI、国际化或日志规则。
- `pmf-logger` 的 UI 追踪规则以 `sharedUI/src/commonMain/kotlin/top/kagg886/pmf/util/logger.kt` 为准，必须使用 `TraceEffect`。
- 技能说明使用简体中文；业务日志字符串必须遵守 `pmf-logger` 的英文要求。

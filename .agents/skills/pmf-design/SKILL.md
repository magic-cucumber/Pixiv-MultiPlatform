---
name: pmf-design
description: 规范 Pixiv-MultiPlatform-2 的 Compose 页面与组件封装、状态容器、加载错误刷新转场、异步操作和 Preview。用于修改 @Composable、screen 内容、页面状态、动画或可复用 UI。
---

# PMF Compose 设计

## 入口与 Content

- 公开 screen 入口只收集状态、判断页面状态、获取导航等环境依赖，并把整理后的状态、数据和回调传给私有 `Content`。
- 私有 `Content` 只通过参数接收状态、数据和事件回调，不直接获取 ViewModel、NavController 或其他页面环境，使其能够独立预览和复用。
- 一个公开入口中不要堆叠所有 UI；将加载、成功、空内容和错误状态拆为私有 Content composable。

## 页面状态与过渡

1. 为互斥的加载、成功、错误等状态建立明确的状态容器，由容器统一分发。
2. 默认使用淡入淡出。只有业务语义明确且布局适合时，才为特定状态转换增加展开或收缩，不让所有状态共用复杂尺寸动画。
3. 状态容器使用 `Modifier.fillMaxSize()`，为状态切换提供稳定的 `contentAlignment`，避免旧内容和新内容切换时发生无意义的位置跳动。
4. 首次加载且没有可展示内容时才使用整页加载或错误态；已有内容刷新时保留内容，使用页面内轻量进度反馈。

## 异步内容与操作

- 依赖图片或其他异步内容的浮层控件，在依赖准备完成后再显示，并用 `AnimatedVisibility` 平滑出现和消失。
- 异步操作开始时立即切换加载状态并阻止重复触发；成功、失败和取消都必须恢复状态，优先使用 `try/finally`。
- Lazy 容器内的局部操作状态以数据项稳定 ID 作为 `remember` 键，避免复用条目时串状态。

## 复用与预览

- 优先复用项目已有的加载、错误、图片、滚动条和带进度操作组件，不在页面重复实现同类控件。
- 为加载、成功、错误等主要状态分别提供私有 Preview。Preview 直接调用私有 Content，不连接真实数据源、导航或业务状态容器。
- 列表、网格、Paging、滚动条和稳定 key 规则由 `pmf-list` 负责；弹层形态和独立路由由 `pmf-overlay` 负责。

修改 `screen.kt` 或 `model.kt` 时，同时加载 `pmf-logger`；UI 层追踪必须遵守该技能规定的 `TraceEffect` 约束。

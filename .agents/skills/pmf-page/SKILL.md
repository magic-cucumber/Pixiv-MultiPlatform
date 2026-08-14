---
name: pmf-page
description: 设计 Pixiv-MultiPlatform-2 的 route.kt、model.kt、screen.kt、Nav3 页面层级、状态模型和一次性副作用。用于新增页面、调整页面注册、导航、ViewModel 状态或 screen 与 model 的边界。
---

# PMF 页面架构

## 页面文件

- 功能页面通常按同一目录组织 `route.kt`、`model.kt` 和 `screen.kt`；简单叶子页面可以只有 `screen.kt`。
- `route.kt` 只组合当前容器的直接子页面。根路由只注册一级页面，子容器继续在自己的 `route.kt` 中注册子页面，不把所有页面平铺到根图。
- 路由标识定义在对应的 `screen.kt`；一个 `screen.kt` 只能声明一个 route class 和一个与其绑定的公开 screen 入口。

## model.kt

1. 只放页面状态、用户操作和一次性结果，不绘制界面。
2. 把界面需要展示的数据放入状态，把导航、提示等一次性结果放入 effect。
3. 让状态表达可观察的页面决策，例如加载、成功、空内容和失败；不要让 screen 通过隐式条件拼凑业务状态。
4. 设计或修改 `model.kt`、ViewModel 状态或 effect 时，必须同时加载 `pmf-logger`。

## screen.kt

1. 读取状态、获取导航环境，并把用户操作转交给 model；具体 UI 结构遵循 `pmf-design`。
2. 在 screen 层收集一次性 effect 并执行导航。临时页面完成跳转且不应返回时，先移除该页面再进入目标页面。
3. 容器页面负责显示子页面；子页面只负责自身内容。容器共享状态可以向子页面提供，但页面私有状态不得跨功能长期复用。
4. 任何 `screen.kt` 或 `model.kt` 的设计、修改都必须同时加载 `pmf-logger`，即使最终判断某个简单分支无需新增日志。

## 对话框路由

对话框是独立页面：使用独立的 route class、`screen.kt` 和公开入口，并通过 `navigate3` 的 `dialog` 接口注册。普通页面不得直接创建临时 `Dialog` 或 `AlertDialog`；交互形态由 `pmf-overlay` 决定。

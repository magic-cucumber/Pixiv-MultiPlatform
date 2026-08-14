---
name: pmf-overlay
description: 设计 Pixiv-MultiPlatform-2 的 Dialog、Bottom Sheet 和 Drawer 交互，并决定其信息量、表单复杂度与独立路由注册方式。用于弹窗、蒙层、选择器和浮层流程。
---

# PMF 弹层设计

## 形态选择

- `dialog` 用于确认、取消、提示等简短信息；附带表单时限制为不超过两个轻量操作项，例如开关、按钮或输入框。
- `bottom sheet` 用于三个及以上表单项、选择器、填写后提交等连续处理流程；只有一到两个操作项时优先使用 `dialog`。
- `drawer` 用于当前内容旁的列表型补充信息，例如相关推荐；不要用它承载主要表单流程或替代普通页面导航。

## 路由边界

1. 每个对话框使用独立 route class、独立 `screen.kt` 和独立页面入口。
2. 使用 `navigate3` 的 `dialog` 接口注册对话框路由。
3. 普通页面的 screen/content 中不得直接创建临时 `Dialog` 或 `AlertDialog`。
4. 路由组合和 effect 导航遵循 `pmf-page`；页面内部的公开入口、私有 Content 和 Preview 遵循 `pmf-design`。

## 实现检查

- 先根据交互信息量选择形态，再决定布局，不要仅凭视觉样式互换组件。
- 关闭、取消和提交行为必须通过页面回调或 effect 表达，不能让私有 Content 直接持有导航环境。
- 弹层中的用户可见文本加载 `pmf-i18n`。

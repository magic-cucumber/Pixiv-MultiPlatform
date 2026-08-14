---
name: pmf-list
description: 规范 Pixiv-MultiPlatform-2 的 Lazy 列表、网格、Paging、滚动状态、滚动条、稳定 key、占位内容、间距和瀑布流行为。用于列表分页、滚动稳定性或加载状态相关任务。
---

# PMF 列表与分页

## 间距与滚动

- 使用同一个 `PaddingValues` 推导内容边距、横向间距和纵向间距，不在页面不同位置重复硬编码。
- 水平方向间距根据 `LocalLayoutDirection` 计算，不能假设固定的 LTR 左右关系。
- 列表或网格与滚动条必须共享同一个滚动状态，并通过 `rememberScrollbarAdapter` 连接；使用同一层叠容器对齐，不维护第二个滚动状态。

## Lazy 数据项

1. 每个数据项提供稳定且唯一的 key；分页条目未就绪时使用与正式条目尺寸稳定的占位内容。
2. 访问 Paging 条目时区分 `peek(index)` 与 `items[index]`：生成 key 等不应触发加载的读取使用 `peek`，需要进入视口并触发 Paging 提示时才使用索引访问。
3. 底部加载和“没有更多内容”属于 `loadState.append`；不要用 `refresh` 状态代替底部状态。
4. 局部条目状态必须绑定稳定业务 ID，不能只依赖 Lazy 容器位置。

## 瀑布流与追踪

- 瀑布流插画或图片保留原始可变宽高比；不要为了对齐改成所有卡片等高的固定 dp。
- 需要观察列表状态、挂载、滚动或 Paging 变化时，加载 `pmf-logger`，并使用 `TraceEffect` 记录聚合状态，不要为每次重组刷日志。
- 滚动追踪应记录可诊断的首尾可见索引、总数、偏移或加载状态，并用去重/聚合控制频率。

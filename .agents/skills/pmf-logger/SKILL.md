---
name: pmf-logger
description: 规范 Pixiv-MultiPlatform-2 的 ViewModel、Repository 和 Compose UI 业务日志与状态追踪。用于新增或修改 screen.kt、model.kt、ViewModel、Repository、请求缓存异常流程，以及 UI 加载、分页、滚动和状态变化追踪。
---

# PMF 业务日志

## 唯一实现来源

先阅读 `sharedUI/src/commonMain/kotlin/top/kagg886/pmf/util/logger.kt`，以当前实现为准。项目提供 `LoggerScope`、`TraceEffect` 和 `v/d/i/w/e` 扩展，不要自行创建没有统一 tag 的 Kermit logger。

## UI 层追踪强制规则

UI 层的状态、挂载、布局、分页、滚动和可观察业务路径追踪必须使用 `TraceEffect`。不得用另一个 `LaunchedEffect` 加手动 logger、普通 `trace` 或页面私有 logger 替代 UI 追踪机制。

`TraceEffect` 的第一个 key 必须是字符串 tag，且至少传入一个 key；其余 key 用于决定 `LaunchedEffect` 何时重新执行。使用稳定、可诊断的状态快照作为 key，避免把每次重组都变成日志事件。

```kotlin
import top.kagg886.pmf.util.TraceEffect
import top.kagg886.pmf.util.i

TraceEffect("FeatureContent", screenState, itemCount, appendState) {
    i("Feature UI state changed (state=$screenState, itemCount=$itemCount, append=$appendState)")
}
```

连续滚动或高频状态必须使用 `snapshotFlow`、去重或聚合，只记录首尾可见索引、总数、偏移、加载状态等能重建路径的摘要。不得记录每次重组、每个像素或每个条目的无诊断价值事件。

## 类、ViewModel 与 Repository

- 新增 ViewModel 或 Repository 时使用 `plugins/logger/processor` 提供的 `@Logger`。注解只能标注可访问的类，不能标注 `private`、`protected`、局部类或嵌套不可访问类；同包使用 processor 生成的 `logger` 扩展。
- ViewModel 至少记录入口触发、关键参数的安全摘要、准备设置的 state、准备发送的 effect、成功、取消、异常和失败后的 UI 状态。
- Repository 记录调用入口、数据源选择、请求或持久化结果摘要、数据转换/校验、缓存或默认值 fallback 和返回给上层的失败类型。
- Repository 记录数据层事实，ViewModel 记录 UI 决策；不要对同一事件重复打印相同日志。
- `try/catch`、空结果、超时、解析失败、缓存失效和提前返回必须与实际返回值、state 或 effect 一致地记录。

## 日志等级

- `v`：复杂函数的有诊断价值的控制流、关键分支和提前返回；已有更高等级事实日志时不要重复记录。
- `d`：独立功能段的预期、实际结果和有效性，例如请求响应摘要、缓存命中或转换条数。
- `i`：重要运行状态和业务决策，例如最终采用的数据源、state 或 effect。
- `w`：发现非预期状态但继续走 fallback；说明原因、受影响原路径和 fallback。
- `e`：非预期退出并改变或中断流程；异常时传入 throwable，说明最终结果。
- `a`：仅保留既有基础设施约定，不在新的 ViewModel 或 Repository 业务路径中使用。

## 安全与语言

- 所有业务日志消息使用英文；代码标识和状态名可以保留以便定位。
- 不记录 access token、refresh token、密码、Cookie、完整授权 URL、用户隐私原文或其他敏感信息；必要时只记录是否存在、长度、脱敏 ID 或错误类型。
- 不为简单赋值、纯转发和显而易见的空值判断增加日志。

涉及 `screen.kt` 或 `model.kt` 时，即使 UI 追踪最终不需要新增事件，也必须加载本技能完成日志覆盖判断。

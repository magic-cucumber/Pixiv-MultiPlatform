---
name: pmf-i18n
description: 维护 Pixiv-MultiPlatform-2 的页面国际化资源、双语文案、YAML 目录映射和生成资源访问器。用于新增或修改用户可见文本、i18n YAML、Lang.string 或 stringResource。
---

# PMF 国际化

## 资源位置

- Kotlin 页面位于 `sharedUI/src/commonMain/kotlin/top/kagg886/pmf/ui/screen/`。
- 对应资源位于 `sharedUI/i18n/src/i18n/`，按页面目录镜像组织。
- `screen.kt` 的页面路径必须与对应 YAML 文件路径一致，例如 `screen/login/screen.kt` 对应 `i18n/login.yaml`，`screen/main/home/screen.kt` 对应 `i18n/main/home.yaml`。

## 编写规则

1. 所有用户可见文本都放入国际化资源，界面代码不得直接写自然语言。
2. 每个页面使用独立 YAML；只有确实跨页面共享的文案才放入共享功能文件。
3. 每个 key 使用稳定、语义明确的名称，并同时提供 `en_us` 和 `zh_cn`。
4. 同一个 key 只能定义一次。新增语言时扩展同一 key，不复制另一套 key。
5. Kotlin 通过生成的资源访问器获取文案，例如 `stringResource(Lang.string.feature_action)`；不要在界面内判断语言或维护映射表。
6. 新增、移动或重命名页面时，同步新增、移动或重命名对应 YAML，并检查所有资源访问器导入。

## 检查重点

- 用 `rg` 检查新增 key 是否已存在，确认不存在重复定义。
- 检查英文和简体中文是否都是自然、完整且与操作语义一致的文案。
- 检查 Kotlin 使用的是资源标识，而不是硬编码文本或语言分支。
- 不把页面专属文案塞进巨型公共文件。

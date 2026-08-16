package top.kagg886.pmf.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * 全局翻译服务宿主 ViewModel。
 *
 * 挂载在全局 ViewModelStore（App 根节点经 [top.kagg886.pmf.ui.util.globalViewModel]
 * 实例化，随应用生命周期存活）：应用启动且 AI 翻译已启用时在 [init] 中后台预热
 * koog 客户端，把引擎/客户端初始化移出首次翻译路径；设置页在配置变更后调用 [prewarm]
 * 即时重建会话。
 */
class TranslateViewModel(
    private val translator: KoogTranslator,
) : ViewModel() {
    init {
        if (isAiTranslateEnabled()) {
            viewModelScope.launch { translator.prewarm() }
        }
    }

    /** 配置变更后预热（幂等：会话缓存命中即跳过构建，无有效配置或失败仅记日志）。 */
    fun prewarm() {
        viewModelScope.launch { translator.prewarm() }
    }
}

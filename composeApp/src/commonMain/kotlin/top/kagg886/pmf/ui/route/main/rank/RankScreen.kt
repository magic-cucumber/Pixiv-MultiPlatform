package top.kagg886.pmf.ui.route.main.rank

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import top.kagg886.pixko.module.illust.RankCategory
import top.kagg886.pmf.NavigationItem
import top.kagg886.pmf.composeWithAppBar
import top.kagg886.pmf.res.*
import top.kagg886.pmf.ui.component.TabContainer
import top.kagg886.pmf.ui.util.IllustFetchScreen
import top.kagg886.pmf.util.stringResource

@Serializable
data object RankRoute : NavKey

private val tabTitleResources = mapOf(
    "day" to Res.string.rank_day,
    "week" to Res.string.rank_week,
    "month" to Res.string.rank_month,
    "day_male" to Res.string.rank_day_male,
    "day_female" to Res.string.rank_day_female,
    "week_original" to Res.string.rank_week_original,
    "week_rookie" to Res.string.rank_week_rookie,
)

@Composable
fun RankScreen() = NavigationItem.RANK.composeWithAppBar {
    var index by rememberSerializable { mutableIntStateOf(0) }
    TabContainer(
        modifier = Modifier.fillMaxSize(),
        tab = RankCategory.entries,
        tabTitle = {
            Text(
                text =
                stringResource(
                    it.content.let { c ->
                        tabTitleResources[c].apply {
                            println("$c --> $this")
                        }!!
                    },
                ),
            )
        },
        current = RankCategory.entries[index],
        scrollable = true,
        onCurrentChange = { index = RankCategory.entries.indexOf(it) },
    ) { type ->
        val model = koinViewModel<IllustRankScreenModel>(key = "$type") {
            parametersOf(type)
        }
        IllustFetchScreen(model)
    }
}

package top.kagg886.pmf.ui.route.main.follow

import top.kagg886.pixko.module.user.UserLikePublicity
import top.kagg886.pixko.module.user.getFollowingList
import top.kagg886.pmf.backend.pixiv.PixivConfig
import top.kagg886.pmf.ui.util.AuthorFetchViewModel
import top.kagg886.pmf.ui.util.flowOf
import top.kagg886.pmf.ui.util.page

class FollowViewModel(
    private val restrict: UserLikePublicity = UserLikePublicity.PUBLIC,
) : AuthorFetchViewModel() {
    private val id = PixivConfig.pixiv_user!!.userId

    override fun source() = flowOf(30) { params ->
        params.page { page ->
            client.getFollowingList(id) {
                this.page = page
                publicity = restrict
            }
        }
    }
}

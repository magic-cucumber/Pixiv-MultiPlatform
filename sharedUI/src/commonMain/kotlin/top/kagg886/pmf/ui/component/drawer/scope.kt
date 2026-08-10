package top.kagg886.pmf.ui.component.drawer

public interface DrawerSheetPageScaffoldScope {
    public fun close()
}

internal class DrawerSheetPageScaffoldScopeImpl(
    private val onClose: () -> Unit,
) : DrawerSheetPageScaffoldScope {
    override fun close(): Unit = onClose()
}

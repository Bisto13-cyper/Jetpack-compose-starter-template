class FavoritesFeature : FeatureModule {
    override val id = "favorites"
    override val title = "Favorites"
    override val icon = FeatureIcon.Emoji("⭐")
    override val defaultAccent = Color(0xFFFB7185)

    @Composable
    override fun Content(onBack: () -> Unit) {
        var currentGroupId by remember { mutableStateOf<String?>(null) }

        BackHandler {
            if (currentGroupId != null) currentGroupId = null
            else onBack()
        }

        FavoritesScreen(
            parentId = currentGroupId,
            onOpenGroup = { groupId -> currentGroupId = groupId }
        )
    }
}

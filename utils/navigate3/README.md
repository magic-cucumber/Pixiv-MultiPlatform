# Navigate3

`navigate3` is a Kotlin Multiplatform wrapper around AndroidX Navigation 3. It provides a typed
tree DSL while persisting one leaf-only back stack. Every parent route renders the flat stack of
its direct children with its own `NavDisplay`.

## Keys and state scopes

All keys implement `SerializableNavKey` and must be serializable by the application's generated
`SerializersModule`.

```kotlin
@Serializable
data class Detail(val id: Long) : SerializableNavKey
```

`SerializableNavKey.contentKey()` identifies the shared ViewModel scope. Navigation never uses it
to deduplicate records. Two retained entries with the same value share their ViewModels, and the
store is cleared only after the final back-stack reference and final exit-animation composition
have both disappeared.

Navigation 3 content instances use a separate internal occurrence key. This allows two different
screens to share a ViewModel content key without reusing each other's Composable content.

## Graph DSL

```kotlin
val graph = createNavGraph<SerializableNavKey> {
    route(
        parent = Root,
        startDestination = Welcome,
        content = ::RootScreen,
    ) {
        destination<Welcome> { WelcomeScreen() }
        destination<Login> { LoginScreen() }

        route(
            parent = Main,
            startDestination = Home,
            content = ::MainScreen,
        ) {
            destination<Home> { HomeScreen() }
            destination<Detail> { DetailScreen(it.id) }
            dialog<SettingsDialog> { SettingsDialogScreen() }
        }
    }
}
```

Routes may be nested to any depth. A route target recursively resolves through its start
destination until it reaches a visible leaf. Start destinations must be direct children.

Each route's content receives a slot containing its own child `NavDisplay`:

```kotlin
@Composable
fun MainScreen(content: @Composable () -> Unit) {
    MainScaffold { content() }
}
```

Dialogs are ordinary leaf entries carrying `DialogSceneStrategy` metadata. Because they are stored
in the direct parent's flat stack, the underlying sibling remains available to the dialog scene.

## Configuration and restoration

The generated application serializer module is mandatory. Do not replace it with an empty module
or an Android-only saver.

```kotlin
val config = NavConfig<SerializableNavKey>(
    serializersModule = ApplicationNavSerializerModule,
)

val controller = rememberNavController(
    graph = graph,
    startDestination = Welcome,
    config = config,
)

NavDisplay(controller = controller, config = config)
```

Display settings propagate to nested routes. A route can override scene strategies and transitions
with `NavConfigOverride`; serialization always uses the root module.

## Back stack and navigation

`controller.backStack` is a `List<T>` of retained visible leaves in back-navigation order. Parent
routes are structural and never persisted in it. `controller.currentPath` is the unique graph path
from the root route to the current leaf.

`navigate` always appends one resolved leaf, including duplicate keys and duplicate content keys:

```kotlin
controller.navigate(Detail(42))
controller.navigate(Detail(42))
```

`popBackStack()` removes exactly one top record and refuses to remove the final record.

Use `update` for atomic pop-and-push transitions:

```kotlin
controller.update {
    pop()
    push(Login)
}
```

The update scope only exposes top operations. It validates the completed candidate and publishes it
with one Compose snapshot commit, so observers never see the temporary empty state.

## ViewModels

Route content can create a model with the normal Compose API because its route entry is the current
`ViewModelStoreOwner`:

```kotlin
val mainModel = androidx.lifecycle.viewmodel.compose.viewModel<MainViewModel> {
    MainViewModel()
}
```

Destinations use `top.kagg886.pmf.util.nav3.viewModel`. Supplying a factory creates an entry-scope
model; omitting it searches the current scope followed by every ancestor route and fails if no
existing instance is found.

```kotlin
val parentModel = viewModel<MainViewModel>()
val pageModel = viewModel<DetailViewModel> { DetailViewModel() }
```

# Navigate3

`navigate3` is a small Kotlin Multiplatform wrapper around AndroidX Navigation 3 for Compose. It provides:

- a type-safe navigation graph DSL;
- immutable navigation chains for nested routes;
- a `NavController` with a Compose-observable back stack; and
- route-scoped and destination-scoped `ViewModel` stores.

The module is located at `utils/navigate3` and exposes its API from
`top.kagg886.pmf.util.nav3`.

## Add the dependency

```kotlin
dependencies {
    implementation(project(":utils:navigate3"))
}
```

## Define navigation keys

Use one sealed type for all navigation keys. A route key should be an
argument-free value, normally a `data object`. Destinations may carry arguments.

```kotlin
sealed interface Screen

data object Login : Screen
data object Main : Screen
data object Gallery : Screen
data class Detail(val id: Long) : Screen
```

## Create a graph

Register top-level destinations with `destination`. Use `route` for a nested
section: its `content` is the route chrome, while destinations declared inside
the route are its children.

```kotlin
val graph = createNavGraph<Screen> {
    destination<Login> {
        LoginScreen()
    }

    route(
        parent = Main,
        startDestination = Gallery,
        content = { child ->
            MainScaffold(content = child)
        },
    ) {
        destination<Gallery> {
            GalleryScreen()
        }
        destination<Detail> { screen ->
            DetailScreen(id = screen.id)
        }
    }
}
```

Each destination type may be registered only once. A route's `startDestination`
should be one of the destinations declared in that route.

## Create and display a controller

`NavController` needs a graph and an initial destination. It immediately adds
the initial destination to its back stack.

```kotlin
val controller = remember { NavController(graph, Gallery) }

NavDisplay(controller)
```

`NavDisplay` connects the controller to Navigation 3. By default it installs
state-saving and `ViewModel`-store entry decorators. Pass `modifier` or
`entryDecorators` when custom display behaviour is needed.

## Navigate and go back

Navigate with a destination key. The graph derives the complete chain for
nested destinations automatically.

```kotlin
controller.navigate(Detail(id = 42)) // Chain: Main -> Detail(42)
controller.navigate(Login)

if (!controller.popBackStack()) {
    // There was no history entry to remove.
}
```

When navigating, the controller retains the shared part of the current and
target chains, pops the remaining records, and appends the target chain. This
means switching from `Detail(42)` to `Login` removes the `Main` branch before
showing `Login`.

You can also navigate with an explicit `NavChain`, but it must contain exactly
the registered parent keys followed by the destination key. Invalid chains fail
fast when Navigation 3 creates their entry.

## ViewModel ownership

Each history record owns a `ViewModelStore` for every route in its chain.

- A `ViewModel` requested in route chrome uses the route's store and is shared
  by that route's children.
- A `ViewModel` requested inside a destination uses the destination entry's
  normal store.
- When a history record is popped, every route store owned by that record is
  cleared. Call `controller.clear()` when the controller itself is no longer
  needed to clear all route stores and the back stack.

While route chrome is composing, `LocalNavRouteViewModelStoreOwner` exposes the
route owner. A child destination can use it to access the same route-scoped
`ViewModel`:

```kotlin
val routeOwner = checkNotNull(LocalNavRouteViewModelStoreOwner.current)
val sharedViewModel: MainViewModel = viewModel(viewModelStoreOwner = routeOwner)
```

Use the normal `viewModel()` call in a destination when the model should belong
only to that destination.

## Public API

| Type or function                   | Purpose                                                                  |
|------------------------------------|--------------------------------------------------------------------------|
| `createNavGraph`                   | Creates a `NavGraph` with the navigation DSL.                            |
| `NavGraph`                         | Resolves destination keys to navigation chains and Navigation 3 entries. |
| `NavChain`                         | An immutable list of route keys plus its visible destination.            |
| `NavController`                    | Owns the observable back stack and route `ViewModel` stores.             |
| `NavDisplay`                       | Renders a `NavController` through Navigation 3.                          |
| `LocalNavRouteViewModelStoreOwner` | Provides the current route-scoped `ViewModelStoreOwner`.                 |

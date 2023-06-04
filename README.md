# Website Checker

Introducing a native Android app specifically designed for website maintainers! The
app's key functionality involves checking the operational status of user specified
websites. Manual checks are possible, but the app also performs these automatically
in the background, every two hours, provided a network connection is available. In
instances where a website is found to be unreachable, the app promptly sends out a
notification.

This is my first project using [Kotlin](https://kotlinlang.org/), which was a
nontrivial but rewarding experience. I used
[Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) with
[OkHttp](https://square.github.io/okhttp/) for asynchronous fetching of websites,
and [Jetpack Compose](https://developer.android.com/jetpack/compose) with themed
widgets from Google's [Material 3](https://m3.material.io/) design language for the
UI.

## Screenshots

### App Icon

<img src="https://github.com/KenanHanke/website-checker/assets/110426806/40562d32-264d-4797-bd5f-cb3b8cca7e1a" width=25%>

### User Interface

<img src="https://github.com/KenanHanke/website-checker/assets/110426806/356f9074-548f-49b9-9bf9-1cf79a6bd9b9" width=50%>

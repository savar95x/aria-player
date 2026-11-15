package dev.savar.aria.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** One visit to a page: [route] like "artist/SZA"; [key] is unique per visit and keys its saved state. */
@Immutable
data class Page(val route: String, val key: Int)

/**
 * Aria's own tiny navigator (replaces Navigation-Compose's NavHost), modelled on how Android's
 * Fragment transitions behave in Retro/Metro: the moment you navigate, the page you're leaving stops
 * being touchable and only animates out, and the page you're going to is live and on top from its
 * first frame. So you can tap straight through any animation, and a tap during a back animation can
 * never land on the page sliding away (that used to start a song from the closing artist page).
 */
class Pages(initial: List<Page>) {
    val stack = mutableStateListOf<Page>().apply { addAll(initial) }
    private var nextKey = (initial.maxOfOrNull { it.key } ?: 0) + 1

    /** Direction of the latest change: picks the slide direction. */
    var popping by mutableStateOf(false)

    /** Popped pages still animating out. They're drawn, but can't be touched. */
    val leaving = mutableStateListOf<Page>()

    val top: Page get() = stack.last()
    val previous: Page? get() = stack.getOrNull(stack.size - 2)

    fun push(route: String) {
        popping = false
        stack.add(Page(route, nextKey++))
    }

    /** Returns the page removed, so its saved state can be dropped. */
    fun pop(): Page? {
        if (stack.size <= 1) return null
        popping = true
        return stack.removeAt(stack.lastIndex).also { leaving.add(it) }
    }

    fun popToRoot(): List<Page> {
        if (stack.size <= 1) return emptyList()
        popping = true
        val removed = stack.drop(1)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        leaving.addAll(removed)
        return removed
    }

    companion object {
        val Saver = listSaver<Pages, String>(
            save = { p -> p.stack.map { "${it.key}|${it.route}" } },
            restore = { saved -> Pages(saved.map { Page(it.substringAfter('|'), it.substringBefore('|').toInt()) }) },
        )
    }
}

// Same motion as before: shared-axis slide + fade, shorter on the way back.
private val Enter = slideInHorizontally(tween(300, easing = Motion.EmphasizedDecelerate)) { it / 10 } + fadeIn(tween(180))
private val Exit = slideOutHorizontally(tween(200, easing = Motion.EmphasizedAccelerate)) { -it / 10 } + fadeOut(tween(100))
private val PopEnter = slideInHorizontally(tween(240, easing = Motion.EmphasizedDecelerate)) { -it / 10 } + fadeIn(tween(150))
private val PopExit = slideOutHorizontally(tween(180, easing = Motion.EmphasizedAccelerate)) { it / 10 } + fadeOut(tween(90))

/**
 * Draws the pages. Every page that's on screen gets its own AnimatedVisibility driven by [transition]
 * (so predictive back can seek it), the destination is always composed last (on top, gets touches
 * first), and any page that isn't the destination swallows touches that reach it.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PageHost(
    pages: Pages,
    transition: Transition<Page>,
    states: SaveableStateHolder,
    modifier: Modifier = Modifier,
    content: @Composable (Page) -> Unit,
) {
    val target = transition.targetState
    // Forget popped pages once their exit has played (and their saved state with them).
    LaunchedEffect(transition.currentState, target) {
        val done = pages.leaving.filter { it != transition.currentState && it != target }
        done.forEach { states.removeState(it.key) }
        pages.leaving.removeAll(done)
    }
    Box(modifier) {
        val onScreen = (pages.stack + pages.leaving).distinctBy { it.key }.sortedBy { if (it == target) 1 else 0 }
        onScreen.forEach { page ->
            key(page.key) {
                transition.AnimatedVisibility(
                    visible = { it == page },
                    enter = if (pages.popping) PopEnter else Enter,
                    exit = if (pages.popping) PopExit else Exit,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalAnimatedScope provides this@AnimatedVisibility) {
                            states.SaveableStateProvider(page.key) { content(page) }
                        }
                        if (page != target) {
                            // Leaving: still drawn while it animates out, but never touchable.
                            Box(
                                Modifier.matchParentSize().pointerInput(Unit) {
                                    awaitEachGesture {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            event.changes.forEach { it.consume() }
                                            if (event.changes.none { it.pressed }) break
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

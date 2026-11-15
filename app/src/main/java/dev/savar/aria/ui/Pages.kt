package dev.savar.aria.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue

/** One visit to a page: [route] like "artist/SZA"; [key] is unique per visit and keys its saved state. */
@Immutable
data class Page(val route: String, val key: Int)

/**
 * Aria's own tiny navigator (replaces Navigation-Compose's NavHost).
 *
 * Why: NavHost draws the page you're leaving *on top* until its exit animation ends, so a tap during
 * a back animation landed on the leaving page (tapping an artist in the list started a song from the
 * artist page sliding away). It also jammed when back was pressed mid-animation. Here the page you're
 * going to is always on top ([transitionSpec] raises its z-index), and nothing ever blocks input:
 * like Metro, you can tap through any animation.
 */
class Pages(initial: List<Page>) {
    val stack = mutableStateListOf<Page>().apply { addAll(initial) }
    private var nextKey = (initial.maxOfOrNull { it.key } ?: 0) + 1
    private var z = 0f

    /** Direction of the latest change, read by [transitionSpec]. */
    var popping by mutableStateOf(false)

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
        return stack.removeAt(stack.lastIndex)
    }

    fun popToRoot(): List<Page> {
        if (stack.size <= 1) return emptyList()
        popping = true
        val removed = stack.drop(1)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        return removed
    }

    /** Push: slide forward. Pop: slide back. Either way the destination is drawn (and touched) on top. */
    fun transitionSpec(scope: AnimatedContentTransitionScope<Page>): ContentTransform {
        z += 1f
        return if (popping) {
            ContentTransform(Motion.PopEnter(scope), Motion.PopExit(scope), z, SizeTransform(clip = false))
        } else {
            ContentTransform(Motion.Enter(scope), Motion.Exit(scope), z, SizeTransform(clip = false))
        }
    }

    companion object {
        val Saver = listSaver<Pages, String>(
            save = { p -> p.stack.map { "${it.key}|${it.route}" } },
            restore = { saved -> Pages(saved.map { Page(it.substringAfter('|'), it.substringBefore('|').toInt()) }) },
        )
    }
}

package de.gumerbaev.actual.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * A user-facing message that is resolved against a [Context] only when displayed,
 * so it always uses the currently selected app language.
 */
sealed class UiText {
    data class Res(@StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText()
    data class Raw(val value: String) : UiText()

    fun asString(context: Context): String = when (this) {
        is Res -> context.getString(resId, *args.toTypedArray())
        is Raw -> value
    }

    companion object {
        fun res(@StringRes resId: Int, vararg args: Any): UiText = Res(resId, args.toList())
    }
}

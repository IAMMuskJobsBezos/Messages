package org.fossify.messages.extensions

import android.graphics.drawable.GradientDrawable
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.addDividerIfNeeded() {
    if (itemDecorationCount == 0) {
        val divider = DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply {
            setDrawable(GradientDrawable().apply {
                setColor(resources.getColor(org.fossify.commons.R.color.divider_grey, context.theme))
                setSize(0, resources.getDimensionPixelSize(org.fossify.commons.R.dimen.divider_height))
            })
        }
        addItemDecoration(divider)
    }
}

fun RecyclerView.onScroll(
    onScrolled: ((dx: Int, dy: Int) -> Unit),
    onScrollStateChanged: ((newState: Int) -> Unit)
) {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            onScrolled.invoke(dx, dy)
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            onScrollStateChanged.invoke(newState)
        }
    })
}

package com.github.ushie

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.lytefast.flexinput.adapters.AttachmentPreviewAdapter
import com.lytefast.flexinput.model.Media

typealias AttachmentPreviewAdapterViewHolder<T> = AttachmentPreviewAdapter<T>.b

@Suppress("unused")
@AliucordPlugin
class QuickSpoilAttachment : Plugin() {
    override fun start(context: Context) {
        patcher.after<AttachmentPreviewAdapter<*>>(
            "onBindViewHolder",
            RecyclerView.ViewHolder::class.java,
            Integer.TYPE,
        ) {
            val holder = it.args[0] as AttachmentPreviewAdapterViewHolder<*>

            val item = this.a.get(it.args[1] as Int)
            if (item !is Media)
                return@after

            with(holder.p) {
                b.setOnLongClickListener {
                    item.spoiler = !item.spoiler
                    holder.mBindingAdapter.notifyDataSetChanged()
                    true
                }
                c.setOnLongClickListener {
                    item.spoiler = !item.spoiler
                    holder.mBindingAdapter.notifyDataSetChanged()
                    true
                }

            }
        }
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}

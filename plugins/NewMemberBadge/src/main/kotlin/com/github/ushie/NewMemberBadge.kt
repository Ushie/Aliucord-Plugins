package com.github.ushie

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.utils.DimenUtils.dp
import com.aliucord.utils.ViewUtils.findViewById
import com.discord.stores.StoreStream.getUserSettingsSystem
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.entries.ChatListEntry
import com.discord.widgets.chat.list.entries.MessageEntry
import com.discord.widgets.roles.RoleIconView
import com.github.ushie.ui.NewMemberBadgeResource
import com.lytefast.flexinput.R.d.role_icon_size
import kotlin.Int
import kotlin.Suppress
import kotlin.apply
import kotlin.let

// Aliucord Plugin annotation. Must be present on the main class of your plugin
// Plugin class. Must extend Plugin and override start and stop
// Learn more: https://github.com/Aliucord/documentation/blob/main/plugin-dev/1_introduction.md#basic-plugin-structure

@AliucordPlugin(
    requiresRestart = false // Whether your plugin requires a restart after being installed/updated
)
@Suppress("unused")
class NewMemberBadge : Plugin() {
    lateinit var newMemberBadgeResource: NewMemberBadgeResource
    val log: Logger = Logger("NewMemberBadge")

    override fun load(context: Context) {
        newMemberBadgeResource = NewMemberBadgeResource(resources!!)
    }

    @SuppressLint("UseCompatLoadingForDrawables", "UseKtx")
    override fun start(context: Context) {
        patcher.after<WidgetChatListAdapterItemMessage>(
            "onConfigure",
            Int::class.java,
            ChatListEntry::class.java
        ) { param ->
            val entry = param.args[1] as MessageEntry
            val message = entry.message

            if (message.isLoading) return@after

            if (entry.author.joinedAt == null) return@after
            val hasJoinedRecently = (message.timestamp.g() - entry.author.joinedAt!!.g()) < 7L * 24 * 60 * 60 * 1000
            if (!hasJoinedRecently) {
                return@after
            }

            val headerView = this.itemView.findViewById<ConstraintLayout>("chat_list_adapter_item_text_header")
            val timeStampView = headerView.findViewById<View>("chat_list_adapter_item_text_timestamp")
            val nameView = headerView.findViewById<TextView>("chat_list_adapter_item_text_name")
            val roleIconView = headerView.findViewById<RoleIconView>("chat_list_adapter_item_text_role_icon")
            val hasRoleIcon = roleIconView.visibility == View.VISIBLE
            val margin = 4.dp

            headerView.let { container ->
                if (container.findViewWithTag<View>("new_member_badge") != null) return@let

                val newMemberBadge = ImageView(context).apply {
                    id = View.generateViewId()
                    tag = "new_member_badge"
                    val fontScale = getUserSettingsSystem().fontScale
                    val baseSize = 51f
                    val maxSize = resources.getDimensionPixelSize(role_icon_size)
                    val size = (baseSize * (fontScale / 100f)).toInt().coerceAtMost(maxSize)
                    log.info("Role icon size: ${size}, role icon height: ${roleIconView.layoutParams.height}, font scale: $fontScale")
                    log.info((fontScale / 100f).toString())
                    log.info((baseSize * (fontScale / 100f)).toString())

                    layoutParams = ConstraintLayout.LayoutParams(size, size).apply {
                        startToEnd = if (hasRoleIcon) roleIconView.id else nameView.id
                        endToStart = timeStampView.id
                    }

                    setImageDrawable(newMemberBadgeResource.getDrawable("ic_new_member_badge_24dp"))
                    contentDescription = "New Member Badge"
                    setOnClickListener {
                        Utils.showToast("I'm new here, say hi!")
                    }
                }

                val timestampIndex = container.indexOfChild(timeStampView)
                container.addView(newMemberBadge, timestampIndex)

                ConstraintSet().apply {
                    clone(headerView)

                    connect(nameView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                    connect(nameView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(nameView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                    if (hasRoleIcon) {
                        connect(nameView.id, ConstraintSet.END, roleIconView.id, ConstraintSet.START)
                    } else {
                        connect(nameView.id, ConstraintSet.END, newMemberBadge.id, ConstraintSet.START)
                    }

                    if (hasRoleIcon) {
                        connect(roleIconView.id, ConstraintSet.START, nameView.id, ConstraintSet.END, margin)
                        connect(roleIconView.id, ConstraintSet.END, newMemberBadge.id, ConstraintSet.START)
                        connect(roleIconView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                        connect(roleIconView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                        connect(newMemberBadge.id, ConstraintSet.START, roleIconView.id, ConstraintSet.END, margin)
                        connect(newMemberBadge.id, ConstraintSet.END, timeStampView.id, ConstraintSet.START)
                    } else {
                        connect(newMemberBadge.id, ConstraintSet.START, nameView.id, ConstraintSet.END, margin)
                        connect(newMemberBadge.id, ConstraintSet.END, timeStampView.id, ConstraintSet.START)
                    }

                    connect(newMemberBadge.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(newMemberBadge.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                    connect(timeStampView.id, ConstraintSet.START, newMemberBadge.id, ConstraintSet.END, margin)
                    connect(timeStampView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                    connect(timeStampView.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                    connect(timeStampView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)

                    setHorizontalChainStyle(nameView.id, ConstraintSet.CHAIN_PACKED)

                    applyTo(headerView)
                }
            }
        }
    }


    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}

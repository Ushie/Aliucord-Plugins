package com.github.ushie

import android.content.Context
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.rn.user.RNUserProfile
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.utils.RxUtils.await
import com.discord.utilities.rest.RestAPI
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.entries.ChatListEntry
import com.discord.widgets.chat.list.entries.MessageEntry

@Suppress("unused")
@AliucordPlugin
class PronounsInChat : Plugin() {
    private var cache = HashMap<Long, String?>()
    val log = com.aliucord.Logger("PronounsInChat")

    override fun start(context: Context) {
        patcher.after<WidgetChatListAdapterItemMessage>(
            "onConfigure",
            Int::class.java,
            ChatListEntry::class.java
        ) { param ->
            val entry = param.args[1] as MessageEntry
            val message = entry.message

            if (message.isLoading) return@after

            val author = message.author
            val guildId = message.guildId

            val timestampId = Utils.getResId(
                "chat_list_adapter_item_text_timestamp",
                "id"
            )
            val timestampView = itemView.findViewById<TextView>(timestampId)
                ?: return@after

            cache[author.id]?.let {
                setPronounsTextView(timestampView, it)
                return@after
            }

            Utils.threadPool.execute {
                try {
                    val pronouns = getUserPronouns(author.id, guildId)

                    cache[author.id] = pronouns

                    if (pronouns != null) {
                        Utils.mainThread.post {
                            if (message.author.id == author.id) {
                                setPronounsTextView(timestampView, pronouns)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    log.error("Failed to fetch pronouns for user ${author.id}", t)
                }
            }
        }
    }

    private fun fetchUserProfile(
        userId: Long,
        guildId: Long?
    ): RNUserProfile? =
        RestAPI.api
            .userProfileGet(userId, false, guildId)
            .await()
            .first
                as? RNUserProfile

    private fun getUserPronouns(
        userId: Long,
        guildId: Long?
    ): String? =
        fetchUserProfile(userId, guildId)
            ?.run {
                guildMemberProfile?.pronouns
                    ?.takeIf(String::isNotEmpty)
                    ?: userProfile?.pronouns
                        ?.takeIf(String::isNotEmpty)
            }

    private fun setPronounsTextView(
        textView: TextView,
        pronouns: String
    ) {
        textView.text = "${textView.text} • ${pronouns}"
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}

package com.github.ushie

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.instead
import com.discord.api.guildscheduledevent.GuildScheduledEvent
import com.discord.stores.StoreGuildScheduledEvents
import java.util.Collections.emptyList

@Suppress("unused")
@AliucordPlugin
class HideEvents : Plugin() {
    override fun start(context: Context) {
        patcher.instead<StoreGuildScheduledEvents>("getGuildScheduledEvents",
            Long::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!
        ) {
            return@instead emptyList<GuildScheduledEvent>()
        }

        patcher.instead<StoreGuildScheduledEvents>("getAllGuildScheduledEvents") {
            return@instead emptyMap<Long, List<GuildScheduledEvent>>()
        }
    }


    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}

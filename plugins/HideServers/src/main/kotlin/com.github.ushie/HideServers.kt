package com.github.ushie

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.FragmentManager
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.patcher.before
import com.aliucord.utils.lazyMethod
import com.discord.databinding.WidgetGuildContextMenuBinding
import com.discord.utilities.color.ColorCompat
import com.discord.widgets.guilds.contextmenu.GuildContextMenuViewModel
import com.discord.widgets.guilds.contextmenu.WidgetGuildContextMenu
import com.discord.widgets.guilds.list.GuildListItem
import com.discord.widgets.guilds.list.WidgetGuildListAdapter
import com.discord.widgets.guilds.list.WidgetGuildsListViewModel
import com.google.gson.reflect.TypeToken
import com.lytefast.flexinput.R

@AliucordPlugin
class HideServers : Plugin() {
    init {
        settingsTab = SettingsTab(PluginSettings::class.java).withArgs(settings)
    }

    private val hiddenServers = mutableSetOf<Long>()
    private val hideServerViewId = View.generateViewId()
    private val hiddenServersType = object : TypeToken<MutableSet<Long>>() {}.type
    private val getBindingMethod by lazyMethod<WidgetGuildContextMenu>("getBinding")

    private var adapter: WidgetGuildListAdapter? = null
    private var editMode = false
    private var originalItems: List<GuildListItem> = emptyList()

    override fun start(context: Context) {
        hiddenServers += settings.getObject("hiddenServers", mutableSetOf(), hiddenServersType)

        val visibleIcon = AppCompatResources.getDrawable(context, R.e.design_ic_visibility)
        val hiddenIcon = AppCompatResources.getDrawable(context, R.e.design_ic_visibility_off)

        patcher.before<WidgetGuildsListViewModel>(
            "onItemClicked", GuildListItem::class.java, Context::class.java, FragmentManager::class.java
        ) { param ->
            if (param.args[0] != GuildListItem.HelpItem.INSTANCE) return@before
            editMode = !editMode
            param.result = null
            Utils.mainThread.post { adapter?.setItems(originalItems, false) }
        }

        patcher.before<WidgetGuildListAdapter>(
            "setItems",
            List::class.java,
            Boolean::class.java
        ) { param ->
            adapter = param.thisObject as? WidgetGuildListAdapter

            @Suppress("UNCHECKED_CAST")
            originalItems = (param.args[0] as? List<GuildListItem>) ?: return@before

            val shouldShowVisibilityToggle = settings.getBool("showVisibilityToggle", true)

            var items = originalItems

            if (shouldShowVisibilityToggle && GuildListItem.HelpItem.INSTANCE !in items) {
                items = items.toMutableList().apply {
                    add(lastIndex.coerceAtLeast(0), GuildListItem.HelpItem.INSTANCE)
                }
            }

            if (!editMode) {
                items = items.mapNotNull { item ->
                    when (item) {
                        is GuildListItem.GuildItem -> item.takeUnless { it.guild.id in hiddenServers }
                        is GuildListItem.FolderItem -> item.hideServers(hiddenServers)
                        else -> item
                    }
                }
            }

            param.args[0] = items
        }

        patcher.after<WidgetGuildContextMenu>(
            "configureUI", GuildContextMenuViewModel.ViewState::class.java
        ) { param ->
            val state = param.args[0] as? GuildContextMenuViewModel.ViewState.Valid ?: return@after
            val binding = getBindingMethod.invoke(this) as? WidgetGuildContextMenuBinding ?: return@after
            val layout = binding.e.parent as? LinearLayout ?: return@after
            val guild = state.guild

            if (layout.findViewById<TextView>(hideServerViewId) != null) return@after

            val isHidden = guild.id in hiddenServers
            val icon = (if (isHidden) visibleIcon else hiddenIcon)?.mutate()?.apply {
                setTint(ColorCompat.getThemedColor(layout.context, R.b.colorInteractiveNormal))
            }

            layout.addView(TextView(layout.context, null, 0, R.i.ContextMenuTextOption).apply {
                id = hideServerViewId
                layoutParams = binding.e.layoutParams
                text = if (isHidden) "Unhide Server" else "Hide Server"
                setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)

                setOnClickListener {
                    if (guild.id in hiddenServers) hiddenServers.remove(guild.id)
                    else hiddenServers.add(guild.id)

                    settings.setObject("hiddenServers", hiddenServers)
                    Utils.mainThread.post { adapter?.setItems(originalItems, false) }
                    layout.visibility = View.GONE
                }
            })
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        adapter = null
        editMode = false
        hiddenServers.clear()
    }
}

private fun GuildListItem.FolderItem.hideServers(hiddenServers: Set<Long>): GuildListItem.FolderItem? {
    val visibleGuilds = guilds.filterNot { it.id in hiddenServers }
    if (visibleGuilds.isEmpty()) return null
    return copy(
        folderId, color, name, isOpen, visibleGuilds, isAnyGuildSelected,
        isAnyGuildConnectedToVoice, isAnyGuildConnectedToStageChannel,
        mentionCount, isUnread, isTargetedForFolderAddition
    )
}

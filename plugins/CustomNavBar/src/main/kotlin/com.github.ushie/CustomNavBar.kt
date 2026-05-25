package com.github.ushie

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.SettingsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.patcher.before
import com.aliucord.patcher.instead
import com.aliucord.settings.delegate
import com.aliucord.utils.DimenUtils.dp
import com.aliucord.utils.ViewUtils.findViewById
import com.aliucord.utils.accessField
import com.aliucord.wrappers.ChannelWrapper.Companion.type
import com.discord.api.channel.Channel
import com.discord.api.channel.ChannelUtils
import com.discord.stores.StoreMentions
import com.discord.stores.StoreNavigation
import com.discord.stores.StoreStream
import com.discord.utilities.color.ColorCompat
import com.discord.widgets.guilds.list.GuildListItem
import com.discord.widgets.guilds.list.WidgetGuildListAdapter
import com.discord.widgets.tabs.NavigationTab
import com.discord.widgets.tabs.TabsHostBottomNavigationView
import com.discord.widgets.tabs.`TabsHostBottomNavigationView$updateView$4`
import com.google.gson.reflect.TypeToken
import com.lytefast.flexinput.R
import java.lang.reflect.Type

val bottomNavItemsType: Type = object : TypeToken<List<NavItem>>() {}.type

@Suppress("unused")
@AliucordPlugin
class CustomNavBar : Plugin() {
    init {
        settingsTab = SettingsTab(PluginSettings::class.java).withArgs(settings)
    }

    val StoreMentions.privateChannels: HashSet<Long> by accessField()

    private var shouldChangeChannels = false
    private var isHomeSelected = false

    private lateinit var messagesTab: ConstraintLayout
    private lateinit var messagesIcon: ImageView
    private lateinit var messagesBadge: TextView
    private var forceShowGuildsUntil: Long = 0L

    var SettingsAPI.hideGuildList by settings.delegate("hide_guild_list", true)
    var SettingsAPI.hideMessagesButton by settings.delegate("hide_messages_button", true)
    var SettingsAPI.hideDmsInGuildsList by settings.delegate("hide_dms_in_guild_list", true)
    var SettingsAPI.lastChannel by settings.delegate("last_channel", 0L)

    override fun start(context: Context) {
        val navItems = settings.getObject(
            "bottom_nav_items",
            DEFAULTS,
            bottomNavItemsType
        ).filter { it.enabled }

        if (navItems == DEFAULTS) return

        val hasMessagesEnabled = navItems.any { it.name == "Messages" && it.enabled }

        patchNavBar(navItems, hasMessagesEnabled)
        if (hasMessagesEnabled) patchGuildList()
    }

    override fun stop(context: Context) = patcher.unpatchAll()

    private fun patchNavBar(navItems: List<NavItem>, hasMessagesEnabled: Boolean) {
        if (hasMessagesEnabled) {
            patcher.after<`TabsHostBottomNavigationView$updateView$4`>("onClick", View::class.java) {
                if (settings.lastChannel != 0L) {
                    StoreStream.getMessagesLoader().jumpToMessage(settings.lastChannel, 1L)
                }

                forceShowGuildsUntil = System.currentTimeMillis() + 1000
            }
        }

        patcher.after<TabsHostBottomNavigationView>(
            "updateView",
            NavigationTab::class.java,
            Function1::class.java,
            Boolean::class.javaPrimitiveType!!,
            Long::class.javaPrimitiveType!!,
            Set::class.java,
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
            Function0::class.java,
            Function0::class.java,
            Function0::class.java,
        ) { param ->
            val selectedTab = param.args[0] as NavigationTab
            isHomeSelected = selectedTab == NavigationTab.HOME

            messagesTab = createMessagesTab(this.context)
            val items = mapOf(
                "Home" to findViewById<ConstraintLayout>("tabs_host_bottom_nav_home_item"),
                "Friends" to findViewById<ConstraintLayout>("tabs_host_bottom_nav_friends_item"),
                "Search" to findViewById<FrameLayout>("tabs_host_bottom_nav_search_item"),
                "Mentions" to findViewById<FrameLayout>("tabs_host_bottom_nav_mentions_item"),
                "Settings" to findViewById<FrameLayout>("tabs_host_bottom_nav_user_settings_item"),
                "Messages" to messagesTab
            )

            findViewById<LinearLayout>("tabs_host_bottom_nav_tabs_container").apply {
                removeAllViews()
                weightSum = navItems.size.toFloat()
                navItems.mapNotNull { items[it.name] }.forEach(::addView)
                if (hasMessagesEnabled) {
                    updateNavBar(this)
                }
            }
        }
    }

    private fun patchGuildList() {
        val hideMessages = settings.hideMessagesButton
        val hideDms = settings.hideDmsInGuildsList

        if (hideDms) {
            patcher.instead<GuildListItem.PrivateChannelItem>("isUnread") { false }

            patcher.instead<GuildListItem.PrivateChannelItem>("getMentionCount") { 0 }
        }

        patcher.before<WidgetGuildListAdapter>(
            "setItems",
            List::class.java,
            Boolean::class.java
        ) { param ->
            @Suppress("UNCHECKED_CAST")
            val items = param.args[0] as? List<GuildListItem> ?: return@before

            param.args[0] = items.filterNot { item ->
                when (item) {
                    is GuildListItem.PrivateChannelItem -> hideDms
                    is GuildListItem.FriendsItem -> hideMessages
                    is GuildListItem.DividerItem -> hideMessages && hideDms
                    else -> false
                }
            }
        }
    }

    private fun updateNavBar(view: View) {
        val homeIcon = view.findViewById<ImageView>("tabs_host_bottom_nav_home_icon")
        val homeBadge = view.findViewById<TextView>("tabs_host_bottom_nav_home_notifications_badge")

        val selectedChannelType = StoreStream.getChannelsSelected().selectedChannel.type
        val isInDMs = isHomeSelected && (selectedChannelType == Channel.DM || selectedChannelType == Channel.GROUP_DM)

        val mentions = StoreStream.getMentions()
        val dmMentions = mentions.privateChannels.sumOf { mentions.mentionCounts[it] ?: 0 }
        val homeMentions = mentions.mentionCounts
            .filterKeys { it !in mentions.privateChannels }
            .values
            .sum()

        homeIcon.isActive(!isInDMs)
        messagesIcon.isActive(isInDMs)

        homeBadge.updateBadge(homeMentions.coerceAtLeast(0))
        messagesBadge.updateBadge(dmMentions)

        if (!settings.hideGuildList || settings.lastChannel == 0L) return
        val guilds = view.rootView.findViewById<FrameLayout?>("widget_guilds") ?: return

        guilds.visibility = if (forceShowGuildsUntil - System.currentTimeMillis() <= 0 && isInDMs) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun createMessagesTab(context: Context): ConstraintLayout {
        return ConstraintLayout(context, null, 0, R.i.TabsHostBottomNavItemWithNotifications).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)

            messagesIcon = ImageView(context, null, 0, R.i.TabsHostBottomNavIcon).apply {
                id = View.generateViewId()

                val icon = ContextCompat.getDrawable(context, R.e.ic_guild_list_dms_24dp)?.mutate()
                icon?.setTint(
                    ColorCompat.getThemedColor(
                        context,
                        if (isSelected) R.b.colorTabsIconActive else R.b.colorInteractiveNormal
                    )
                )
                setImageDrawable(icon)

                contentDescription = context.getString(R.h.direct_messages)

                layoutParams = ConstraintLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                }
            }

            messagesBadge = TextView(context, null, 0, R.i.TabsHostBottomNavNotificationBadge).apply {
                id = View.generateViewId()

                layoutParams = ConstraintLayout.LayoutParams(WRAP_CONTENT, 20.dp).apply {
                    circleConstraint = messagesIcon.id
                    circleAngle = 135f
                    circleRadius = 12.dp
                }
            }

            this.addView(messagesIcon)
            this.addView(messagesBadge)

            setOnClickListener {
                val channel = StoreStream.getChannelsSelected().selectedChannel

                if (channel != null && !ChannelUtils.B(channel)) {
                    settings.lastChannel = channel.k()
                }

                if (StoreStream.Companion!!.tabsNavigation.selectedTab != NavigationTab.HOME) {
                    StoreStream.Companion!!.tabsNavigation.selectTab(NavigationTab.HOME, false)
                }
                StoreStream.getGuildSelected().set(0L)
                StoreStream.Companion!!.navigation.setNavigationPanelAction(StoreNavigation.PanelAction.CLOSE)
            }
        }
    }
}

private fun TextView.updateBadge(count: Int) {
    text = count.toString()
    visibility = if (count > 0) View.VISIBLE else View.GONE
}

private fun ImageView.isActive(active: Boolean) {
    this.setColorFilter(
        ColorCompat.getThemedColor(
            this.context,
            if (active) R.b.colorTabsIconActive else R.b.colorInteractiveNormal
        )
    )
}

package com.github.ushie

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageButton
import android.widget.LinearLayout
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils.dp
import com.aliucord.views.Button
import com.aliucord.views.Divider
import com.discord.views.CheckedSetting
import com.lytefast.flexinput.R
import java.util.Collections

data class NavItem(
    val name: String,
    var enabled: Boolean
)

val DEFAULTS = listOf(
    NavItem("Home", true),
    NavItem("Messages", false),
    NavItem("Friends", true),
    NavItem("Search", true),
    NavItem("Mentions", true),
    NavItem("Settings", true)
)

class PluginSettings(private val settings: SettingsAPI) : SettingsPage() {
    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Custom Nav Bar")
        setActionBarSubtitle("Settings")

        load()
    }

    private fun load() {
        val context = requireContext()

        val items = settings.getObject(
            "bottom_nav_items",
            DEFAULTS,
            bottomNavItemsType
        ).toMutableList()

        linearLayout.removeAllViews()

        items.forEachIndexed { index, item ->
            linearLayout.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

                    addView(
                        Utils.createCheckedSetting(
                            context,
                            CheckedSetting.ViewType.SWITCH,
                            item.name,
                            null
                        ).apply {
                            isChecked = item.enabled
                            setOnCheckedListener { checked ->
                                items[index].enabled = checked
                                settings.setObject("bottom_nav_items", items)
                                load()
                            }
                        },
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    )

                    addView(arrowButton(items, index, index - 1, R.e.ic_arrow_up_24dp, index > 0))
                    addView(arrowButton(items, index, index + 1, R.e.ic_arrow_down_24dp, index < items.lastIndex))
                }
            )
        }

        if (items.any { it.name == "Messages" && it.enabled }) {
            addView(spacer(20.dp))
            addView(Divider(context))
            addView(
                Utils.createCheckedSetting(
                    context,
                    CheckedSetting.ViewType.SWITCH,
                    "Hide guild list when in DMs",
                    null
                ).apply {
                    isChecked = settings.getBool("hide_guild_list", true)
                    setOnCheckedListener { settings.setBool("hide_guild_list", it) }
                }
            )

            addView(
                Utils.createCheckedSetting(
                    context,
                    CheckedSetting.ViewType.SWITCH,
                    "Hide messages button in the server list",
                    null
                ).apply {
                    isChecked = settings.getBool("hide_messages_button", true)
                    setOnCheckedListener { settings.setBool("hide_messages_button", it) }
                }
            )

            addView(
                Utils.createCheckedSetting(
                    context,
                    CheckedSetting.ViewType.SWITCH,
                    "Hide DMs in the server list",
                    null
                ).apply {
                    isChecked = settings.getBool("hide_dms_in_guild_list", true)
                    setOnCheckedListener { settings.setBool("hide_dms_in_guild_list", it) }
                }
            )
            addView(Divider(context))
            addView(spacer(20.dp))
        }

        linearLayout.addView(resetButton())

    }

    fun spacer(height: Int) = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            MATCH_PARENT,
            height
        )
    }

    private fun resetButton() = Button(context).apply {
        text = "Reset to default"
        setOnClickListener {
            settings.resetSettings()
            load()
        }
    }

    private fun arrowButton(
        items: List<NavItem>,
        from: Int,
        to: Int,
        icon: Int,
        enabled: Boolean
    ) =
        ImageButton(context).apply {
            setImageResource(icon)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.3f
            background = null

            setOnClickListener {
                Collections.swap(items, from, to)
                settings.setObject("bottom_nav_items", items)
                load()
            }
        }
}


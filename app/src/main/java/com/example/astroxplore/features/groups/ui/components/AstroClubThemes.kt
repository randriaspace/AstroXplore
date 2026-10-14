package com.example.astroxplore.features.groups.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.astroxplore.features.groups.model.GroupModel

data class AstroClubTheme(
    val id: String,
    val name: String,
    val primaryColor: Color,
    val containerColor: Color,
    val onContainerColor: Color,
    val icon: ImageVector,
    val iconName: String,
    val tagLine: String
)

object AstroClubThemes {
    val availableIcons = mapOf(
        "Rocket" to Icons.Default.RocketLaunch,
        "Science" to Icons.Default.Science,
        "Galaxy" to Icons.Default.AutoAwesome,
        "Telescope" to Icons.Default.Explore,
        "Hub" to Icons.Default.Hub,
        "Book" to Icons.Default.MenuBook,
        "Radar" to Icons.Default.Radar,
        "Flare" to Icons.Default.Flare,
        "Satellite" to Icons.Default.Satellite,
        "Atom" to Icons.Default.Psychology,
        "Star" to Icons.Default.Star,
        "Waves" to Icons.Default.Waves
    )

    val availableColors = listOf(
        Triple("Cosmic Blue", Color(0xFF1E88E5), Color(0xFF0D47A1)),
        Triple("Nebula Violet", Color(0xFF7C4DFF), Color(0xFF4A148C)),
        Triple("Supernova Amber", Color(0xFFF57C00), Color(0xFFE65100)),
        Triple("Aurora Teal", Color(0xFF00897B), Color(0xFF004D40)),
        Triple("Pulsar Crimson", Color(0xFFE53935), Color(0xFFB71C1C)),
        Triple("Celestial Cyan", Color(0xFF00ACC1), Color(0xFF006064)),
        Triple("Indigo Deep", Color(0xFF3F51B5), Color(0xFF1A237E)),
        Triple("Pink Nova", Color(0xFFD81B60), Color(0xFF880E4F))
    )

    val options = listOf(
        AstroClubTheme(
            id = "deep_space",
            name = "Cosmic Blue",
            primaryColor = Color(0xFF1E88E5),
            containerColor = Color(0xFF1E88E5).copy(alpha = 0.14f),
            onContainerColor = Color(0xFF0D47A1),
            icon = Icons.Default.Explore,
            iconName = "Telescope",
            tagLine = "Observational Astrophysics & JWST"
        ),
        AstroClubTheme(
            id = "nebula",
            name = "Nebula Violet",
            primaryColor = Color(0xFF7C4DFF),
            containerColor = Color(0xFF7C4DFF).copy(alpha = 0.14f),
            onContainerColor = Color(0xFF4A148C),
            icon = Icons.Default.AutoAwesome,
            iconName = "Galaxy",
            tagLine = "Deep-space Nebulae & Cosmic Structures"
        ),
        AstroClubTheme(
            id = "supernova",
            name = "Supernova Amber",
            primaryColor = Color(0xFFF57C00),
            containerColor = Color(0xFFF57C00).copy(alpha = 0.14f),
            onContainerColor = Color(0xFFE65100),
            icon = Icons.Default.Star,
            iconName = "Supernova",
            tagLine = "Stellar Transients & High Energy"
        ),
        AstroClubTheme(
            id = "aurora",
            name = "Aurora Teal",
            primaryColor = Color(0xFF00897B),
            containerColor = Color(0xFF00897B).copy(alpha = 0.14f),
            onContainerColor = Color(0xFF004D40),
            icon = Icons.Default.SettingsInputAntenna,
            iconName = "Observatory",
            tagLine = "Radio Astronomy & Cosmic Telescopy"
        ),
        AstroClubTheme(
            id = "pulsar",
            name = "Pulsar Crimson",
            primaryColor = Color(0xFFE53935),
            containerColor = Color(0xFFE53935).copy(alpha = 0.14f),
            onContainerColor = Color(0xFFB71C1C),
            icon = Icons.Default.HowToVote,
            iconName = "Consensus",
            tagLine = "Gravitational Waves & Relativity"
        ),
        AstroClubTheme(
            id = "celestial",
            name = "Celestial Cyan",
            primaryColor = Color(0xFF00ACC1),
            containerColor = Color(0xFF00ACC1).copy(alpha = 0.14f),
            onContainerColor = Color(0xFF006064),
            icon = Icons.Default.Bookmark,
            iconName = "Archive",
            tagLine = "Exoplanet Atmospheres & Cosmology"
        )
    )

    fun getTheme(themeId: String?): AstroClubTheme {
        return options.find { it.id == themeId } ?: options[0]
    }

    fun getThemeForGroup(group: GroupModel): AstroClubTheme {
        val focus = group.focusArea.orEmpty()
        val themeMatch = Regex("""\[theme:(\w+)\]""").find(focus)
        val iconMatch = Regex("""\[icon:([\w\s]+)\]""").find(focus)
        val colorMatch = Regex("""\[color:([#\w]+)\]""").find(focus)

        val baseTheme = if (themeMatch != null) {
            options.find { it.id == themeMatch.groupValues[1] } ?: options[0]
        } else {
            val seed = if (group.id.isNotBlank()) group.id else group.name
            val index = (seed.hashCode() % options.size + options.size) % options.size
            options[index]
        }

        val customIconName = iconMatch?.groupValues?.get(1)
        val customIcon = customIconName?.let { availableIcons[it] } ?: baseTheme.icon

        val customColorHex = colorMatch?.groupValues?.get(1)
        val customColor = customColorHex?.let { hex ->
            try {
                Color(android.graphics.Color.parseColor(hex))
            } catch (e: Exception) {
                baseTheme.primaryColor
            }
        } ?: baseTheme.primaryColor

        val containerColor = customColor.copy(alpha = 0.14f)
        val onContainerColor = customColor

        return baseTheme.copy(
            primaryColor = customColor,
            containerColor = containerColor,
            onContainerColor = onContainerColor,
            icon = customIcon,
            iconName = customIconName ?: baseTheme.iconName
        )
    }

    fun cleanFocusArea(raw: String?): String {
        if (raw.isNullOrBlank()) return "Astrophysics"
        val cleaned = raw
            .replace(Regex("""\[theme:\w+\]"""), "")
            .replace(Regex("""\[icon:[\w\s]+\]"""), "")
            .replace(Regex("""\[color:[#\w]+\]"""), "")
            .trim()
        return cleaned.ifBlank { "Astrophysics" }
    }

    fun formatMeetingBadge(schedule: String): String {
        if (schedule.isBlank()) return "Active Circle"
        return when {
            schedule.contains("Weekly", ignoreCase = true) -> {
                val cleaned = schedule.replace("Weekly on ", "").replace("Weekly ", "")
                "Weekly • $cleaned"
            }
            schedule.contains("Monthly", ignoreCase = true) -> {
                val cleaned = schedule.replace("Monthly on ", "").replace("Monthly ", "")
                "Monthly • $cleaned"
            }
            else -> schedule
        }
    }
}

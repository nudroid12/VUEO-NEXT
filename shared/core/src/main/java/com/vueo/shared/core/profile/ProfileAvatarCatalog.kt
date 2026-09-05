package com.vueo.shared.core.profile

import com.vueo.shared.core.R

enum class ProfileAvatarCategory {
    ADULTS,
    KIDS,
    CHARACTERS,
    MALAYSIA,
    LEGACY,
}

data class ProfileAvatarSpec(
    val id: String,
    val drawableRes: Int,
    val category: ProfileAvatarCategory,
)

/**
 * Canonical built-in profile avatar catalogue shared by VUEO Mobile and TV.
 *
 * Profile persistence stores the stable [id], never a drawable resource id.
 * This lets both apps render the same profile backup using their shared assets.
 */
object ProfileAvatarCatalog {
    const val DEFAULT_ID = "avatar_man_1"

    val selectable: List<ProfileAvatarSpec> =
        listOf(
            ProfileAvatarSpec("avatar_man_1", R.drawable.avatar_man_1, ProfileAvatarCategory.ADULTS),
            ProfileAvatarSpec("avatar_man_2", R.drawable.avatar_man_2, ProfileAvatarCategory.ADULTS),
            ProfileAvatarSpec("avatar_man_3", R.drawable.avatar_man_3, ProfileAvatarCategory.ADULTS),
            ProfileAvatarSpec("avatar_man_4", R.drawable.avatar_man_4, ProfileAvatarCategory.ADULTS),
            ProfileAvatarSpec("avatar_man_5", R.drawable.avatar_man_5, ProfileAvatarCategory.ADULTS),
            ProfileAvatarSpec("avatar_woman_1", R.drawable.avatar_woman_1, ProfileAvatarCategory.ADULTS),
            ProfileAvatarSpec("avatar_woman_2", R.drawable.avatar_woman_2, ProfileAvatarCategory.ADULTS),
            ProfileAvatarSpec("avatar_woman_3", R.drawable.avatar_woman_3, ProfileAvatarCategory.ADULTS),
            ProfileAvatarSpec("avatar_woman_4", R.drawable.avatar_woman_4, ProfileAvatarCategory.ADULTS),
            ProfileAvatarSpec("avatar_woman_5", R.drawable.avatar_woman_5, ProfileAvatarCategory.ADULTS),
            ProfileAvatarSpec("avatar_boy_1", R.drawable.avatar_boy_1, ProfileAvatarCategory.KIDS),
            ProfileAvatarSpec("avatar_boy_2", R.drawable.avatar_boy_2, ProfileAvatarCategory.KIDS),
            ProfileAvatarSpec("avatar_boy_3", R.drawable.avatar_boy_3, ProfileAvatarCategory.KIDS),
            ProfileAvatarSpec("avatar_girl_1", R.drawable.avatar_girl_1, ProfileAvatarCategory.KIDS),
            ProfileAvatarSpec("avatar_girl_2", R.drawable.avatar_girl_2, ProfileAvatarCategory.KIDS),
            ProfileAvatarSpec("avatar_girl_3", R.drawable.avatar_girl_3, ProfileAvatarCategory.KIDS),
            ProfileAvatarSpec("avatar_character_1", R.drawable.avatar_character_1, ProfileAvatarCategory.CHARACTERS),
            ProfileAvatarSpec("avatar_character_2", R.drawable.avatar_character_2, ProfileAvatarCategory.CHARACTERS),
            ProfileAvatarSpec("avatar_character_3", R.drawable.avatar_character_3, ProfileAvatarCategory.CHARACTERS),
            ProfileAvatarSpec("avatar_character_4", R.drawable.avatar_character_4, ProfileAvatarCategory.CHARACTERS),
            ProfileAvatarSpec("avatar_character_5", R.drawable.avatar_character_5, ProfileAvatarCategory.CHARACTERS),
            ProfileAvatarSpec("avatar_character_6", R.drawable.avatar_character_6, ProfileAvatarCategory.CHARACTERS),
            ProfileAvatarSpec("avatar_character_7", R.drawable.avatar_character_7, ProfileAvatarCategory.CHARACTERS),
            ProfileAvatarSpec("avatar_character_8", R.drawable.avatar_character_8, ProfileAvatarCategory.CHARACTERS),
            ProfileAvatarSpec("avatar_malaysia_1", R.drawable.avatar_malaysia_1, ProfileAvatarCategory.MALAYSIA),
            ProfileAvatarSpec("avatar_malaysia_2", R.drawable.avatar_malaysia_2, ProfileAvatarCategory.MALAYSIA),
            ProfileAvatarSpec("avatar_malaysia_3", R.drawable.avatar_malaysia_3, ProfileAvatarCategory.MALAYSIA),
            ProfileAvatarSpec("avatar_malaysia_4", R.drawable.avatar_malaysia_4, ProfileAvatarCategory.MALAYSIA),
            ProfileAvatarSpec("avatar_malaysia_5", R.drawable.avatar_malaysia_5, ProfileAvatarCategory.MALAYSIA),
            ProfileAvatarSpec("avatar_malaysia_6", R.drawable.avatar_malaysia_6, ProfileAvatarCategory.MALAYSIA),
            ProfileAvatarSpec("avatar_malaysia_7", R.drawable.avatar_malaysia_7, ProfileAvatarCategory.MALAYSIA),
            ProfileAvatarSpec("avatar_malaysia_8", R.drawable.avatar_malaysia_8, ProfileAvatarCategory.MALAYSIA),
        )

    val legacy: List<ProfileAvatarSpec> =
        listOf(
            ProfileAvatarSpec("avatar_vueo_1", R.drawable.avatar_vueo_1, ProfileAvatarCategory.LEGACY),
            ProfileAvatarSpec("avatar_vueo_2", R.drawable.avatar_vueo_2, ProfileAvatarCategory.LEGACY),
            ProfileAvatarSpec("avatar_vueo_3", R.drawable.avatar_vueo_3, ProfileAvatarCategory.LEGACY),
            ProfileAvatarSpec("avatar_vueo_4", R.drawable.avatar_vueo_4, ProfileAvatarCategory.LEGACY),
        )

    val all: List<ProfileAvatarSpec> = selectable + legacy

    private val byId: Map<String, ProfileAvatarSpec> = all.associateBy(ProfileAvatarSpec::id)

    fun find(id: String): ProfileAvatarSpec? = byId[id]

    fun drawableRes(id: String): Int? = byId[id]?.drawableRes

    fun isBuiltIn(id: String): Boolean = byId.containsKey(id)
}

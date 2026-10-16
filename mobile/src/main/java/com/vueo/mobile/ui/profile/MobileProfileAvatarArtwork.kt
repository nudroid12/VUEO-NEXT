package com.vueo.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

// Bundled portraits have offset source circles. Use the same measured geometry
// as the TV fix; no source artwork or stored avatar IDs are changed.
private data class AvatarArtworkCrop(val x: Float, val y: Float, val radius: Float)
private val AvatarArtworkCrops = mapOf(
    "avatar_boy_1" to AvatarArtworkCrop(0.600436f, 0.560951f, 0.395658f),
    "avatar_boy_2" to AvatarArtworkCrop(0.606057f, 0.562047f, 0.390037f),
    "avatar_boy_3" to AvatarArtworkCrop(0.495726f, 0.567697f, 0.428397f),
    "avatar_character_1" to AvatarArtworkCrop(0.608173f, 0.590849f, 0.387920f),
    "avatar_character_2" to AvatarArtworkCrop(0.618617f, 0.589822f, 0.377476f),
    "avatar_character_3" to AvatarArtworkCrop(0.493224f, 0.582040f, 0.414054f),
    "avatar_character_4" to AvatarArtworkCrop(0.521430f, 0.583089f, 0.413004f),
    "avatar_character_5" to AvatarArtworkCrop(0.550958f, 0.545447f, 0.445136f),
    "avatar_character_6" to AvatarArtworkCrop(0.530377f, 0.556352f, 0.439742f),
    "avatar_character_7" to AvatarArtworkCrop(0.564759f, 0.544646f, 0.431335f),
    "avatar_character_8" to AvatarArtworkCrop(0.570375f, 0.556027f, 0.425719f),
    "avatar_girl_1" to AvatarArtworkCrop(0.524432f, 0.580517f, 0.415577f),
    "avatar_girl_2" to AvatarArtworkCrop(0.547346f, 0.582259f, 0.413835f),
    "avatar_girl_3" to AvatarArtworkCrop(0.567883f, 0.585580f, 0.410513f),
    "avatar_malaysia_1" to AvatarArtworkCrop(0.513895f, 0.514648f, 0.481445f),
    "avatar_malaysia_2" to AvatarArtworkCrop(0.505490f, 0.514039f, 0.482055f),
    "avatar_malaysia_3" to AvatarArtworkCrop(0.494169f, 0.509433f, 0.486660f),
    "avatar_malaysia_4" to AvatarArtworkCrop(0.505075f, 0.510483f, 0.485611f),
    "avatar_malaysia_5" to AvatarArtworkCrop(0.519060f, 0.486994f, 0.477034f),
    "avatar_malaysia_6" to AvatarArtworkCrop(0.505464f, 0.495365f, 0.490630f),
    "avatar_malaysia_7" to AvatarArtworkCrop(0.486839f, 0.494500f, 0.482933f),
    "avatar_malaysia_8" to AvatarArtworkCrop(0.500054f, 0.492379f, 0.488472f),
    "avatar_man_1" to AvatarArtworkCrop(0.512729f, 0.543886f, 0.452208f),
    "avatar_man_2" to AvatarArtworkCrop(0.535629f, 0.548497f, 0.447597f),
    "avatar_man_3" to AvatarArtworkCrop(0.550973f, 0.545426f, 0.445121f),
    "avatar_man_4" to AvatarArtworkCrop(0.564618f, 0.544813f, 0.431476f),
    "avatar_man_5" to AvatarArtworkCrop(0.592563f, 0.547843f, 0.403531f),
    "avatar_woman_1" to AvatarArtworkCrop(0.600757f, 0.549197f, 0.395336f),
    "avatar_woman_2" to AvatarArtworkCrop(0.503470f, 0.552506f, 0.443588f),
    "avatar_woman_3" to AvatarArtworkCrop(0.530452f, 0.556174f, 0.439920f),
    "avatar_woman_4" to AvatarArtworkCrop(0.557371f, 0.559622f, 0.436471f),
    "avatar_woman_5" to AvatarArtworkCrop(0.570219f, 0.556442f, 0.425875f),
)

@Composable
internal fun ProfileAvatarArtwork(avatarId: String, drawableRes: Int, description: String) {
    val crop = AvatarArtworkCrops[avatarId]
    Box(Modifier.fillMaxSize().clip(CircleShape)) {
        Image(
            painter = painterResource(drawableRes),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                if (crop != null) {
                    val zoom = .5f / crop.radius
                    scaleX = zoom
                    scaleY = zoom
                    translationX = (.5f - crop.x) * size.width * zoom
                    translationY = (.5f - crop.y) * size.height * zoom
                }
            },
        )
    }
}

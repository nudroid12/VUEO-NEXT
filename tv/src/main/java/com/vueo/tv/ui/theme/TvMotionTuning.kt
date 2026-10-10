package com.vueo.tv.ui.motion

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** Temporary, TV-only live tuning. Original bypasses every override. */
internal enum class TvMotionGroup(val title: String, val enterMs: Int, val exitMs: Int) {
    FORWARD("Page: forward / Details", 175, 128),
    BACK("Page: back", 150, 112),
    TABS("Home / Search / Library / Settings", 130, 96),
    PLAYER_ROUTE("Enter / leave player", 165, 105),
    FOCUS("Card & button focus", 120, 90),
    HERO("Hero backdrop & copy", 400, 400),
    TOPBAR("Topbar viewport resize", 340, 340),
    NAVIGATION("Topbar / Sidebar reveal", 180, 140),
    PANEL("Dialog & popup", 200, 125),
    WORKSPACE("Player workspace / side panel", 240, 200),
    CONTROLS("Player controls", 240, 180),
    PAUSE("Player pause backdrop", 280, 200),
    IMAGE("Poster image fade", 180, 180);

    val scalar: Boolean get() = this in setOf(FOCUS, HERO, TOPBAR, IMAGE)
    val styles: List<TvMotionStyle> get() = when (this) {
        FOCUS, TOPBAR -> listOf(TvMotionStyle.ORIGINAL, TvMotionStyle.NONE, TvMotionStyle.SCALE)
        HERO, IMAGE -> listOf(TvMotionStyle.ORIGINAL, TvMotionStyle.NONE, TvMotionStyle.FADE)
        else -> TvMotionStyle.entries.toList()
    }
    fun defaults() = TvMotionValues(
        enterMs = enterMs, exitMs = exitMs,
        scale = if (this == FOCUS) 1.05f else if (this == BACK) 1.008f else .990f,
        exitDelayMs = when (this) { FORWARD -> 42; BACK -> 34; TABS -> 28; PLAYER_ROUTE -> 32; else -> 0 },
    )
}

internal enum class TvMotionStyle(val label: String) {
    ORIGINAL("Original"), NONE("Cut / no animation"), FADE("Fade / crossfade"),
    SCALE("Scale"), FADE_SCALE("Fade + scale"), SLIDE_X("Slide horizontal"),
    SLIDE_Y("Slide vertical"), FADE_SLIDE_X("Fade + horizontal"), FADE_SLIDE_Y("Fade + vertical");
    val fades: Boolean get() = this in setOf(FADE, FADE_SCALE, FADE_SLIDE_X, FADE_SLIDE_Y)
    val scales: Boolean get() = this in setOf(SCALE, FADE_SCALE)
}

internal enum class TvMotionCurve(val label: String) {
    OUT("Ease out"), IN_OUT("Ease in/out"), IN("Ease in"), LINEAR("Linear"), CINEMATIC("Slow finish");
    val easing: Easing get() = when (this) {
        OUT -> TvMotion.EaseOut; IN_OUT -> TvMotion.EaseInOut; IN -> TvMotion.EaseIn
        LINEAR -> LinearEasing; CINEMATIC -> CubicBezierEasing(.16f, 1f, .3f, 1f)
    }
}

internal data class TvMotionValues(
    val style: TvMotionStyle = TvMotionStyle.ORIGINAL,
    val enterMs: Int = 175,
    val exitMs: Int = 128,
    val exitDelayMs: Int = 0,
    val curve: TvMotionCurve = TvMotionCurve.OUT,
    val useSpring: Boolean = false,
    val stiffness: Float = 240f,
    val damping: Float = 1f,
    val scale: Float = .990f,
    val travelPercent: Int = 3,
    val initialAlpha: Float = 0f,
) {
    fun bounded() = copy(
        enterMs = enterMs.coerceIn(0, 1500), exitMs = exitMs.coerceIn(0, 1500),
        exitDelayMs = exitDelayMs.coerceIn(0, 500), stiffness = stiffness.coerceIn(40f, 1500f),
        damping = damping.coerceIn(.4f, 1.5f), scale = scale.coerceIn(.8f, 1.2f),
        travelPercent = travelPercent.coerceIn(0, 30), initialAlpha = initialAlpha.coerceIn(0f, 1f),
    )
}

internal object TvMotionTuning {
    private const val PREFS = "vueo_tv_motion_temporary_v1"
    private var loaded = false
    var enabled by mutableStateOf(false)
    var reduceMotion by mutableStateOf(false)
    private var values by mutableStateOf<Map<TvMotionGroup, TvMotionValues>>(emptyMap())
    fun get(group: TvMotionGroup): TvMotionValues = values[group] ?: group.defaults()
    fun active(group: TvMotionGroup) = enabled && get(group).style != TvMotionStyle.ORIGINAL
    fun update(group: TvMotionGroup, next: TvMotionValues) {
        values = values + (group to next.bounded())
        enabled = true
    }
    fun reset(group: TvMotionGroup) { values = values - group }
    fun resetAll(context: Context) {
        values = emptyMap(); enabled = false; reduceMotion = false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val restored = runCatching {
            val json = JSONObject(prefs.getString("values", "{}") ?: "{}")
            TvMotionGroup.entries.mapNotNull { group ->
                val item = json.optJSONObject(group.name) ?: return@mapNotNull null
                val base = group.defaults()
                val style = TvMotionStyle.entries.firstOrNull { it.name == item.optString("style") } ?: base.style
                group to base.copy(
                    style = style.takeIf { it in group.styles } ?: base.style,
                    enterMs = item.optInt("enter", base.enterMs), exitMs = item.optInt("exit", base.exitMs),
                    exitDelayMs = item.optInt("delay", base.exitDelayMs),
                    curve = TvMotionCurve.entries.firstOrNull { it.name == item.optString("curve") } ?: base.curve,
                    useSpring = item.optBoolean("spring", false),
                    stiffness = item.optDouble("stiffness", 240.0).toFloat(),
                    damping = item.optDouble("damping", 1.0).toFloat(),
                    scale = item.optDouble("scale", base.scale.toDouble()).toFloat(),
                    travelPercent = item.optInt("travel", 3), initialAlpha = item.optDouble("alpha", 0.0).toFloat(),
                ).bounded()
            }.toMap()
        }.getOrDefault(emptyMap())
        values = restored
        enabled = prefs.getBoolean("enabled", false)
        reduceMotion = prefs.getBoolean("reduce", false)
    }
    private fun serializedValues(): JSONObject {
        val json = JSONObject()
        values.forEach { (group, value) ->
            json.put(group.name, JSONObject().apply {
                put("style", value.style.name); put("enter", value.enterMs); put("exit", value.exitMs)
                put("delay", value.exitDelayMs); put("curve", value.curve.name); put("spring", value.useSpring)
                put("stiffness", value.stiffness); put("damping", value.damping); put("scale", value.scale)
                put("travel", value.travelPercent); put("alpha", value.initialAlpha)
            })
        }
        return json
    }
    fun exportJson(): String = JSONObject().apply {
        put("schema", "vueo_tv_motion_v1")
        put("enabled", enabled); put("reduceMotion", reduceMotion)
        put("values", serializedValues())
    }.toString(2)
    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", enabled).putBoolean("reduce", reduceMotion)
            .putString("values", serializedValues().toString()).apply()
    }
}

internal fun <T> tvTunedSpec(
    group: TvMotionGroup,
    entering: Boolean,
    original: FiniteAnimationSpec<T>,
): FiniteAnimationSpec<T> {
    if (TvMotionTuning.reduceMotion) return snap()
    if (!TvMotionTuning.active(group)) return original
    val value = TvMotionTuning.get(group)
    if (value.style == TvMotionStyle.NONE) return snap()
    return if (value.useSpring) spring(dampingRatio = value.damping, stiffness = value.stiffness)
    else tween(
        durationMillis = if (entering) value.enterMs else value.exitMs,
        delayMillis = if (entering || group.scalar) 0 else value.exitDelayMs,
        easing = value.curve.easing,
    )
}

internal fun tvTunedFocusScale(original: Float): Float = when {
    TvMotionTuning.reduceMotion -> 1f
    !TvMotionTuning.active(TvMotionGroup.FOCUS) -> original
    TvMotionTuning.get(TvMotionGroup.FOCUS).style == TvMotionStyle.NONE -> 1f
    else -> TvMotionTuning.get(TvMotionGroup.FOCUS).scale
}

internal fun tvTunedAlpha(group: TvMotionGroup, original: Float): Float {
    if (TvMotionTuning.reduceMotion) return 1f
    if (!TvMotionTuning.active(group)) return original
    val value = TvMotionTuning.get(group)
    return if (value.style.fades) value.initialAlpha else 1f
}

internal fun tvTunedScale(group: TvMotionGroup, original: Float): Float {
    if (TvMotionTuning.reduceMotion) return 1f
    if (!TvMotionTuning.active(group)) return original
    val value = TvMotionTuning.get(group)
    return if (value.style.scales) value.scale else 1f
}

internal fun tvTunedEnter(group: TvMotionGroup, original: EnterTransition, direction: Int = 1): EnterTransition {
    if (TvMotionTuning.reduceMotion) return EnterTransition.None
    if (!TvMotionTuning.active(group)) return original
    val value = TvMotionTuning.get(group)
    val style = value.style
    var result = EnterTransition.None
    if (style.fades) result += fadeIn(tvTunedSpec(group, true, tween()), initialAlpha = value.initialAlpha)
    if (style.scales) result += scaleIn(tvTunedSpec(group, true, tween()), initialScale = value.scale)
    if (style == TvMotionStyle.SLIDE_X || style == TvMotionStyle.FADE_SLIDE_X) {
        result += slideInHorizontally(tvTunedSpec(group, true, tween())) { it * value.travelPercent / 100 * direction }
    }
    if (style == TvMotionStyle.SLIDE_Y || style == TvMotionStyle.FADE_SLIDE_Y) {
        result += slideInVertically(tvTunedSpec(group, true, tween())) { it * value.travelPercent / 100 * direction }
    }
    return result
}

internal fun tvTunedExit(group: TvMotionGroup, original: ExitTransition, direction: Int = 1): ExitTransition {
    if (TvMotionTuning.reduceMotion) return ExitTransition.None
    if (!TvMotionTuning.active(group)) return original
    val value = TvMotionTuning.get(group)
    val style = value.style
    var result = ExitTransition.None
    if (style.fades) result += fadeOut(tvTunedSpec(group, false, tween()), targetAlpha = value.initialAlpha)
    if (style.scales) result += scaleOut(tvTunedSpec(group, false, tween()), targetScale = value.scale)
    if (style == TvMotionStyle.SLIDE_X || style == TvMotionStyle.FADE_SLIDE_X) {
        result += slideOutHorizontally(tvTunedSpec(group, false, tween())) { -it * value.travelPercent / 100 * direction }
    }
    if (style == TvMotionStyle.SLIDE_Y || style == TvMotionStyle.FADE_SLIDE_Y) {
        result += slideOutVertically(tvTunedSpec(group, false, tween())) { -it * value.travelPercent / 100 * direction }
    }
    return result
}

internal fun tvTunedContent(group: TvMotionGroup, original: ContentTransform): ContentTransform =
    tvTunedEnter(group, original.targetContentEnter) togetherWith tvTunedExit(group, original.initialContentExit)

package com.vueo.tv.ui

/** Size buckets avoid re-decoding images on every hero animation frame. */
internal object TvImageDecodePolicy {
    fun targetDimension(pixels: Int): Int =
        if (pixels <= 0) 0 else ((pixels.coerceAtMost(1280) + 127) / 128) * 128

    fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (sample < (1 shl 20)) {
            val next = sample * 2
            val fitsTarget = width / next >= targetWidth.coerceAtLeast(1) &&
                height / next >= targetHeight.coerceAtLeast(1)
            val overBudget = ((width.toLong() + sample - 1) / sample) *
                ((height.toLong() + sample - 1) / sample) > 2_000_000L
            if (!fitsTarget && !overBudget) break
            sample = next
        }
        return sample
    }
}

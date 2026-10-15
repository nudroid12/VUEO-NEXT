package com.vueo.mobile.ui.components

fun main() {
    var checks = 0
    fun verify(condition: Boolean) { check(condition); checks++ }
    verify(MobileImageDecodePolicy.targetDimension(0) == 0)
    verify(MobileImageDecodePolicy.targetDimension(129) == 256)
    verify(MobileImageDecodePolicy.targetDimension(Int.MAX_VALUE) == 2048)
    verify(MobileImageDecodePolicy.sampleSize(6000, 4000, 384, 256) == 8)
    verify(MobileImageDecodePolicy.sampleSize(400, 600, 384, 640) == 1)
    verify(MobileImageDecodePolicy.sampleSize(8000, 8000, 2048, 2048) == 8)
    for (width in listOf(1, 64, 512, 1080, 4096, 12000, Int.MAX_VALUE)) {
        for (height in listOf(1, 128, 720, 1920, 8000, Int.MAX_VALUE)) {
            for (target in listOf(128, 384, 1080, 2048)) {
                val sample = MobileImageDecodePolicy.sampleSize(width, height, target, target)
                verify(sample > 0 && (sample and (sample - 1)) == 0)
                val decoded = ((width.toLong() + sample - 1) / sample) *
                    ((height.toLong() + sample - 1) / sample)
                verify(decoded <= 2_000_000)
                if (sample > 1) {
                    val previous = sample / 2
                    val previousPixels = ((width.toLong() + previous - 1) / previous) *
                        ((height.toLong() + previous - 1) / previous)
                    verify(width / sample >= target && height / sample >= target || previousPixels > 2_000_000)
                }
            }
        }
    }
    println("Decode policy: $checks checks passed")
}

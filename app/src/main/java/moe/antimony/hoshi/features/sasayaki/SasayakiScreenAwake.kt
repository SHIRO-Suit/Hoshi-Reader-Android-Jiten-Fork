package moe.antimony.hoshi.features.sasayaki

object SasayakiScreenAwake {
    fun shouldKeepScreenOn(isPlaying: Boolean, autoScroll: Boolean): Boolean =
        isPlaying && autoScroll
}

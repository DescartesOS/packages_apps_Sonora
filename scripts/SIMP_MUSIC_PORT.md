# SimpMusic adaptation

Sonora's inline lyrics timing control adapts `LyricsOffsetBar` from
[maxrave-dev/SimpMusic](https://github.com/maxrave-dev/SimpMusic/blob/5397d0af1b2a849d6994822f269b1995d7d5ca23/composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/ui/component/LyricsOffsetControl.kt),
commit `5397d0af1b2a849d6994822f269b1995d7d5ca23` (GPL-3.0).

This is a source adaptation, not a Git cherry-pick: SimpMusic uses a different
Compose Multiplatform resource, playback, and glass stack. Sonora retains the
100 ms decrement/increment and zero reset interaction, and connects it to its
existing lyrics timing callback and persistence. It adds the existing ±5000 ms
bounds, local accumulated state for quick taps, Material color roles, 48 dp
controls, and accessibility labels. The precise-adjustment dialog remains.

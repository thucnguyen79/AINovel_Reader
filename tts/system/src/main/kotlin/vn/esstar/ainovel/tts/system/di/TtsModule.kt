package vn.esstar.ainovel.tts.system.di

/**
 * Phase 5: the `TtsEngine` binding moved to `tts/neural` (`CompositeTtsEngine` routes between the
 * system engine and sherpa-onnx). `SystemTtsEngine` is injected by its concrete type there.
 */
object TtsModuleNote

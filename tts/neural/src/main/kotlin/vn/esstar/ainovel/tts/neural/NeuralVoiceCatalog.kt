package vn.esstar.ainovel.tts.neural

import vn.esstar.ainovel.tts.api.VoiceModel

/**
 * Vietnamese Piper voices packaged for sherpa-onnx (MIT / Apache-2.0, free), downloaded from the
 * sherpa-onnx `tts-models` GitHub release. Each archive contains the ONNX model, `tokens.txt` and
 * `espeak-ng-data/` (the phonemizer data the model needs).
 */
object NeuralVoiceCatalog {
    private const val BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"

    val models: List<VoiceModel> = listOf(
        VoiceModel(
            id = "vits-piper-vi_VN-vais1000-medium",
            displayName = "VAIS1000 (nữ, chất lượng cao)",
            description = "Giọng nữ miền Bắc, rõ và tự nhiên nhất; cần máy khá (khoảng 1 giây cho mỗi câu).",
            language = "vi-VN",
            downloadUrl = BASE + "vits-piper-vi_VN-vais1000-medium.tar.bz2",
            sizeBytes = 67_200_000,
            quality = 3,
        ),
        VoiceModel(
            id = "vits-piper-vi_VN-25hours_single-low",
            displayName = "25hours (nữ, nhanh)",
            description = "Giọng nữ, nhẹ hơn và tổng hợp nhanh hơn, phù hợp máy yếu.",
            language = "vi-VN",
            downloadUrl = BASE + "vits-piper-vi_VN-25hours_single-low.tar.bz2",
            sizeBytes = 67_100_000,
            quality = 2,
        ),
        VoiceModel(
            id = "vits-piper-vi_VN-vivos-x_low",
            displayName = "VIVOS (nhiều giọng, rất nhẹ)",
            description = "Model nhỏ nhất, nhiều người nói; chất lượng thấp hơn nhưng chạy tốt trên mọi máy.",
            language = "vi-VN",
            downloadUrl = BASE + "vits-piper-vi_VN-vivos-x_low.tar.bz2",
            sizeBytes = 33_400_000,
            quality = 1,
        ),
    )

    fun byId(id: String): VoiceModel? = models.firstOrNull { it.id == id }
}

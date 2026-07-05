package com.kangle.kardleaf.data.utils

object KardLeafContentLimits {
    const val EXPORT_IMAGE_WIDTH_PX = 1080
    const val EXPORT_IMAGE_MIN_HEIGHT_PX = 420
    const val EXPORT_IMAGE_MAX_HEIGHT_PX = 16_000
    const val EXPORT_IMAGE_MAX_CONTENT_CHARS = 24_000
    const val EXPORT_IMAGE_MAX_BODY_LINES = 260
    const val EXPORT_IMAGE_MAX_NOTE_COUNT = 20

    const val WORD_PARAGRAPH_CHUNK_SIZE = 2_000
    const val WORD_EXPORT_MAX_CONTENT_CHARS = 800_000
    const val WORD_EXPORT_MAX_TOTAL_CHARS = 1_200_000

    const val MIND_MAP_MAX_CONTENT_CHARS = 80_000
    const val MIND_MAP_MAX_HEADING_COUNT = 200

    const val IMAGE_DATA_URI_MAX_BYTES = 2L * 1024L * 1024L
    const val IMAGE_IMPORT_MAX_BYTES = 20L * 1024L * 1024L
}

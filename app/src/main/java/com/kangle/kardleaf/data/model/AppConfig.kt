package com.kangle.kardleaf.data.model

data class AppConfig(
    // Filenames
    var pinnedFiles: HashSet<String> = HashSet(),
    // Filename -> Timestamp
    var customTimestamps: HashMap<String, Long> = HashMap(),
)

package com.kangle.kardleaf.data.model

import com.google.gson.annotations.SerializedName

data class AppConfig(
    // Filenames
    @field:SerializedName(value = "pinnedFiles", alternate = ["a"])
    var pinnedFiles: HashSet<String> = HashSet(),
    // Filename -> Timestamp
    @field:SerializedName(value = "customTimestamps", alternate = ["b"])
    var customTimestamps: HashMap<String, Long> = HashMap(),
)

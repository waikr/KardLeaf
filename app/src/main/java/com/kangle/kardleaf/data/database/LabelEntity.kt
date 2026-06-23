package com.kangle.kardleaf.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey
    val name: String,
)

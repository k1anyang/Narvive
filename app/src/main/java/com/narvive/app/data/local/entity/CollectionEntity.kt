package com.narvive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey
    val id: String,                       // UUID
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

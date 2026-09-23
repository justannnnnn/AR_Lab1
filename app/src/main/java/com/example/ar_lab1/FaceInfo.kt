package com.example.ar_lab1

data class FaceInfo(
    val tracking: Boolean = false,
    val vertexCount: Int = 0,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
    val centerZ: Float = 0f,
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f
)
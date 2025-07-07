package com.punyo.kotlinmcpserver.moodle.data

import kotlinx.serialization.Serializable

@Serializable
data class UploadedFile(
    val component: String,
    val contextid: Int,
    val userid: String,
    val filearea: String,
    val filename: String,
    val filepath: String,
    val itemid: Int,
    val license: String,
    val author: String,
    val source: String
)
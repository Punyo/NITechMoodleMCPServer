package com.punyo.kotlinmcpserver.moodle.data

import kotlinx.serialization.Serializable

@Serializable
data class SubmissionStatusResponse(
    val lastattempt: LastAttempt,
    val assignmentdata: AssignmentData,
)

@Serializable
data class LastAttempt(
    val submission: Submission?,
    val submissiongroupmemberswhoneedtosubmit: List<String>,
    val submissionsenabled: Boolean,
    val locked: Boolean,
    val graded: Boolean,
    val canedit: Boolean,
    val caneditowner: Boolean,
    val cansubmit: Boolean,
    val extensionduedate: Long? = null,
    val timelimit: Int,
    val blindmarking: Boolean,
    val gradingstatus: String,
    val usergroups: List<String>
)

@Serializable
data class Submission(
    val id: Int,
    val userid: Int,
    val attemptnumber: Int,
    val timecreated: Long,
    val timemodified: Long,
    val timestarted: Long? = null,
    val status: String,
    val groupid: Int,
    val assignment: Int,
    val latest: Int,
    val plugins: List<SubmissionPlugin>
)

@Serializable
data class SubmissionPlugin(
    val type: String,
    val name: String,
    val fileareas: List<FileArea>? = null
)

@Serializable
data class FileArea(
    val area: String,
    val files: List<SubmissionFile>
)

@Serializable
data class SubmissionFile(
    val filename: String,
    val filepath: String,
    val filesize: Long,
    val fileurl: String,
    val timemodified: Long,
    val mimetype: String,
    val isexternalfile: Boolean
)

@Serializable
data class AssignmentData(
    val attachments: AttachmentData
)

@Serializable
data class AttachmentData(
    val intro: List<IntroFile>
)
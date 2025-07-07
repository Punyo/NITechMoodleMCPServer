package com.punyo.kotlinmcpserver.moodle.data

import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    val fullname: String,
    val userid: Int
)

@Serializable
data class Course(
    val id: Int,
    val shortname: String,
    val fullname: String,
    val displayname: String,
    val idnumber: String,
    val visible: Int,
    val summary: String,
    val summaryformat: Int,
    val format: String,
    val showgrades: Boolean,
    val lang: String,
    val enablecompletion: Boolean,
    val completionhascriteria: Boolean,
    val completionusertracked: Boolean,
    val category: Int,
    val progress: Int? = null,
    val completed: Boolean?,
    val startdate: Long,
    val enddate: Long,
    val marker: Int,
    val lastaccess: Long,
    val isfavourite: Boolean,
    val hidden: Boolean,
    val overviewfiles: List<String>,
    val showactivitydates: Boolean,
    val showcompletionconditions: Boolean?,
    val timemodified: Long
)

@Serializable
data class AssignmentResponse(
    val courses: List<CourseWithAssignments>
)

@Serializable
data class CourseWithAssignments(
    val id: Int,
    val fullname: String,
    val shortname: String,
    val timemodified: Long,
    val assignments: List<Assignment>
)

@Serializable
data class Assignment(
    val id: Int,
    val cmid: Int,
    val course: Int,
    val name: String,
    val nosubmissions: Int,
    val submissiondrafts: Int,
    val sendnotifications: Int,
    val sendlatenotifications: Int,
    val sendstudentnotifications: Int,
    val duedate: Long,
    val allowsubmissionsfromdate: Long,
    val grade: Int,
    val timemodified: Long,
    val completionsubmit: Int,
    val cutoffdate: Long,
    val gradingduedate: Long,
    val teamsubmission: Int,
    val requireallteammemberssubmit: Int,
    val teamsubmissiongroupingid: Int,
    val blindmarking: Int,
    val hidegrader: Int,
    val revealidentities: Int,
    val attemptreopenmethod: String,
    val maxattempts: Int,
    val markingworkflow: Int,
    val markingallocation: Int,
    val requiresubmissionstatement: Int,
    val preventsubmissionnotingroup: Int,
    val configs: List<AssignmentConfig>,
    val intro: String,
    val introformat: Int,
    val introfiles: List<IntroFile>,
    val introattachments: List<IntroFile>,
    val timelimit: Int,
    val submissionattachments: Int
)

@Serializable
data class AssignmentConfig(
    val plugin: String,
    val subtype: String,
    val name: String,
    val value: String
)

@Serializable
data class IntroFile(
    val filename: String,
    val filepath: String,
    val filesize: Long,
    val fileurl: String,
    val timemodified: Long,
    val mimetype: String,
    val isexternalfile: Boolean
)

@Serializable
data class Warning(
    val item: String,
    val itemid: Int,
    val warningcode: String,
    val message: String
)
package com.punyo.kotlinmcpserver.moodle

import com.punyo.kotlinmcpserver.moodle.data.Assignment
import com.punyo.kotlinmcpserver.moodle.data.Course
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent

class MoodleApiClientUtil {
    companion object {
        suspend fun findAndValidateCourse(
            moodleClient: MoodleApiClient,
            userId: Int,
            courseName: String
        ): Pair<Course?, CallToolResult?> {
            val targetCourse = findCourseByName(moodleClient, userId, courseName)

            if (targetCourse == null) {
                val courses = moodleClient.getUserCourses(userId)
                val errorResult = CallToolResult(
                    content = listOf(
                        TextContent(
                            "Course '$courseName' not found. Available courses: ${
                                courses.joinToString(", ") { it.fullname }
                            }"
                        )
                    )
                )
                return Pair(null, errorResult)
            }

            return Pair(targetCourse, null)
        }

        suspend fun findAndValidateAssignment(
            moodleClient: MoodleApiClient,
            courseId: Int,
            assignmentName: String
        ): Pair<Assignment?, CallToolResult?> {
            val assignmentResponse = moodleClient.getAssignments(listOf(courseId))
            val targetAssignment = findAssignmentByName(assignmentResponse, assignmentName)

            if (targetAssignment == null) {
                val errorResult = CallToolResult(
                    content = listOf(
                        TextContent(
                            "Assignment '$assignmentName' not found. Available assignments: ${
                                assignmentResponse.courses
                                    .flatMap { it.assignments }.joinToString(", ") { it.name }
                            }"
                        )
                    )
                )
                return Pair(null, errorResult)
            }

            return Pair(targetAssignment, null)
        }

        /**
         * コース名からコースを検索する
         */
         suspend fun findCourseByName(client: MoodleApiClient, userId: Int, courseName: String): Course? {
            val courses = client.getUserCourses(userId)
            return courses.find { course ->
                course.fullname.contains(courseName, ignoreCase = true) ||
                        course.shortname.contains(courseName, ignoreCase = true) ||
                        course.displayname.contains(courseName, ignoreCase = true)
            }
        }

        /**
         * 課題名から課題を検索する
         */
         fun findAssignmentByName(
            assignmentResponse: com.punyo.kotlinmcpserver.moodle.data.AssignmentResponse,
            assignmentName: String
        ): Assignment? {
            return assignmentResponse.courses
                .flatMap { it.assignments }
                .find { assignment ->
                    assignment.name.contains(assignmentName, ignoreCase = true)
                }
        }
    }
}
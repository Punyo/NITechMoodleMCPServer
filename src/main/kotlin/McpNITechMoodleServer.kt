package com.punyo.kotlinmcpserver

// 名古屋工業大学Moodle用MCPサーバー実装
// このファイルはMoodleApiClientを使用してMCPサーバーを構築します

import com.punyo.kotlinmcpserver.moodle.MoodleApiClient
import com.punyo.kotlinmcpserver.moodle.data.UserInfo
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*
import io.ktor.utils.io.streams.*
import java.io.File

fun runServer() {
    // MoodleApiClientのインスタンスを作成
    val moodleClient = MoodleApiClient()

    // 起動時にユーザー情報を取得してキャッシュ
    val cachedUserInfo: UserInfo = runBlocking {
        try {
            val userInfo = moodleClient.getUserInfo()
            println("ユーザー情報を取得しました: ${userInfo.fullname} (ID: ${userInfo.userid})")
            userInfo
        } catch (e: Exception) {
            System.err.println("ユーザー情報の取得に失敗しました: ${e.message}")
            throw e
        }
    }

    // MCPサーバーインスタンスを作成
    val server = Server(
        Implementation(
            name = "nitechMoodleMCPServer", // サーバー名
            version = "1.0.0" // バージョン
        ),
        ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
    )

    // 指定されたコースの課題一覧を取得するツールを登録
    server.addTool(
        name = "get_assignments",
        description = """
            指定されたコースの課題一覧を取得します。
            入力：course_name（コース名）
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("course_name") {
                    put("type", "string")
                    put("description", "Course name to get assignments from")
                }
            },
            required = listOf("course_name")
        )
    ) { request ->
        try {
            // リクエストからコース名パラメータを取得
            val courseName = request.arguments["course_name"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("The 'course_name' parameter is required."))
                )

            // キャッシュされたユーザー情報を使用
            val userInfo = cachedUserInfo

            // ユーザーのコース一覧を取得
            val courses = moodleClient.getUserCourses(userInfo.userid)

            // 指定されたコース名に一致するコースを検索
            val targetCourse = courses.find { course ->
                course.fullname.contains(courseName, ignoreCase = true) ||
                        course.shortname.contains(courseName, ignoreCase = true) ||
                        course.displayname.contains(courseName, ignoreCase = true)
            }

            if (targetCourse == null) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            "Course '$courseName' not found. Available courses: ${
                                courses.map { it.fullname }.joinToString(", ")
                            }"
                        )
                    )
                )
            }

            // 課題一覧を取得
            val assignmentResponse = moodleClient.getAssignments(listOf(targetCourse.id))

            CallToolResult(content = listOf(TextContent(assignmentResponse.toString())))

        } catch (e: Exception) {
            System.err.println(e.stackTraceToString())
            CallToolResult(
                content = listOf(TextContent("Error getting assignments: ${e.message}\nstacktrace: ${e.stackTraceToString()}"))
            )
        }
    }

    // 指定された課題の提出状況を取得するツールを登録
    server.addTool(
        name = "get_submission_status",
        description = """
            指定された課題の提出状況を取得します。
            入力：course_name（コース名）、assignment_name（課題名）
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("course_name") {
                    put("type", "string")
                    put("description", "Course name")
                }
                putJsonObject("assignment_name") {
                    put("type", "string")
                    put("description", "Assignment name")
                }
            },
            required = listOf("course_name", "assignment_name")
        )
    ) { request ->
        try {
            // リクエストからパラメータを取得
            val courseName = request.arguments["course_name"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("The 'course_name' parameter is required."))
                )

            val assignmentName = request.arguments["assignment_name"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("The 'assignment_name' parameter is required."))
                )

            // キャッシュされたユーザー情報を使用
            val userInfo = cachedUserInfo

            // ユーザーのコース一覧を取得
            val courses = moodleClient.getUserCourses(userInfo.userid)

            // 指定されたコース名に一致するコースを検索
            val targetCourse = courses.find { course ->
                course.fullname.contains(courseName, ignoreCase = true) ||
                        course.shortname.contains(courseName, ignoreCase = true) ||
                        course.displayname.contains(courseName, ignoreCase = true)
            }

            if (targetCourse == null) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Course '$courseName' not found."))
                )
            }

            // 課題一覧を取得
            val assignmentResponse = moodleClient.getAssignments(listOf(targetCourse.id))

            // 指定された課題名に一致する課題を検索
            val targetAssignment = assignmentResponse.courses
                .flatMap { it.assignments }
                .find { assignment ->
                    assignment.name.contains(assignmentName, ignoreCase = true)
                }

            if (targetAssignment == null) {
                val availableAssignments = assignmentResponse.courses
                    .flatMap { it.assignments }
                    .map { it.name }
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            "Assignment '$assignmentName' not found. Available assignments: ${
                                availableAssignments.joinToString(
                                    ", "
                                )
                            }"
                        )
                    )
                )
            }

            // 提出状況を取得
            val submissionStatus = moodleClient.getSubmissionStatus(targetAssignment.id)

            // 提出状況を整形
            val statusText = buildString {
                append("課題「${targetAssignment.name}」の提出状況:\n\n")

                val submission = submissionStatus.lastattempt.submission
                if (submission != null) {
                    append("提出ID: ${submission.id}\n")
                    append("提出状況: ${getSubmissionStatusText(submission.status)}\n")
                    append("提出回数: ${submission.attemptnumber + 1}\n")
                    append("作成日時: ${formatUnixTimestamp(submission.timecreated)}\n")
                    append("最終更新: ${formatUnixTimestamp(submission.timemodified)}\n")

                    if (submission.timestarted != null) {
                        append("開始日時: ${formatUnixTimestamp(submission.timestarted)}\n")
                    }
                } else {
                    append("提出はまだ行われていません。\n")
                }

                append("\n権限情報:\n")
                append("提出有効: ${submissionStatus.lastattempt.submissionsenabled}\n")
                append("ロック状態: ${submissionStatus.lastattempt.locked}\n")
                append("採点状況: ${getGradingStatusText(submissionStatus.lastattempt.gradingstatus)}\n")
                append("採点済み: ${submissionStatus.lastattempt.graded}\n")

                if (submissionStatus.lastattempt.extensionduedate != null) {
                    append("延長期限: ${formatUnixTimestamp(submissionStatus.lastattempt.extensionduedate)}\n")
                }

                if (submissionStatus.lastattempt.timelimit > 0) {
                    append("制限時間: ${submissionStatus.lastattempt.timelimit}秒\n")
                }

                append("\n提出プラグイン:\n")
                submission?.plugins?.forEach { plugin ->
                    append("- ${plugin.name} (${plugin.type})\n")
                    plugin.fileareas?.forEach { fileArea ->
                        append("  ファイル領域: ${fileArea.area} (${fileArea.files.size})\n")
                    }
                }
            }

            CallToolResult(content = listOf(TextContent(statusText)))

        } catch (e: Exception) {
            System.err.println(e.stackTraceToString())
            CallToolResult(
                content = listOf(TextContent("Error getting submission status: ${e.message}"))
            )
        }
    }

    // 課題を提出するツールを登録
    server.addTool(
        name = "submit",
        description = """
            指定された課題にファイルを提出します。
            入力：course_name（コース名）、assignment_name（課題名）、submit_file_path（提出するファイルのパス）
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = buildJsonObject {
                putJsonObject("course_name") {
                    put("type", "string")
                    put("description", "Course name")
                }
                putJsonObject("assignment_name") {
                    put("type", "string")
                    put("description", "Assignment name")
                }
                putJsonObject("submit_file_path") {
                    put("type", "string")
                    put("description", "Path to the file to submit")
                }
            },
            required = listOf("course_name", "assignment_name", "submit_file_path")
        )
    ) { request ->
        try {
            // リクエストからパラメータを取得
            val courseName = request.arguments["course_name"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("The 'course_name' parameter is required."))
                )

            val assignmentName = request.arguments["assignment_name"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("The 'assignment_name' parameter is required."))
                )

            val submitFilePath = request.arguments["submit_file_path"]?.jsonPrimitive?.content
                ?: return@addTool CallToolResult(
                    content = listOf(TextContent("The 'submit_file_path' parameter is required."))
                )

            // ファイルの存在確認
            val file = File(submitFilePath)
            if (!file.exists()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("File not found: $submitFilePath"))
                )
            }

            if (!file.isFile) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Path is not a file: $submitFilePath"))
                )
            }

            // キャッシュされたユーザー情報を使用
            val userInfo = cachedUserInfo

            // ユーザーのコース一覧を取得
            val courses = moodleClient.getUserCourses(userInfo.userid)

            // 指定されたコース名に一致するコースを検索
            val targetCourse = courses.find { course ->
                course.fullname.contains(courseName, ignoreCase = true) ||
                        course.shortname.contains(courseName, ignoreCase = true) ||
                        course.displayname.contains(courseName, ignoreCase = true)
            }

            if (targetCourse == null) {
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            "Course '$courseName' not found. Available courses: ${
                                courses.map { it.fullname }.joinToString(", ")
                            }"
                        )
                    )
                )
            }

            // 課題一覧を取得
            val assignmentResponse = moodleClient.getAssignments(listOf(targetCourse.id))

            // 指定された課題名に一致する課題を検索
            val targetAssignment = assignmentResponse.courses
                .flatMap { it.assignments }
                .find { assignment ->
                    assignment.name.contains(assignmentName, ignoreCase = true)
                }

            if (targetAssignment == null) {
                val availableAssignments = assignmentResponse.courses
                    .flatMap { it.assignments }
                    .map { it.name }
                return@addTool CallToolResult(
                    content = listOf(
                        TextContent(
                            "Assignment '$assignmentName' not found. Available assignments: ${
                                availableAssignments.joinToString(
                                    ", "
                                )
                            }"
                        )
                    )
                )
            }

            // 提出可能かどうか確認
            val submissionStatus = moodleClient.getSubmissionStatus(targetAssignment.id)
            if (!submissionStatus.lastattempt.cansubmit && !submissionStatus.lastattempt.canedit) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Cannot submit to assignment '${targetAssignment.name}'. Submission is not allowed or locked."))
                )
            }

            // ステップ1: ファイルをアップロード
            val fileContent = file.readBytes()
            val uploadedFiles = moodleClient.uploadFile(fileContent, file.name)

            if (uploadedFiles.isEmpty()) {
                return@addTool CallToolResult(
                    content = listOf(TextContent("Failed to upload file: No response from upload endpoint"))
                )
            }

            // ステップ2: アイテムIDを取得
            val itemId = uploadedFiles.first().itemid

            // ステップ3: 提出を保存
            val saveResult = moodleClient.saveSubmission(targetAssignment.id, itemId)

            // 結果を整形
            val resultText = buildString {
                append("ファイル提出が完了しました。\n\n")
                append("コース: ${targetCourse.fullname}\n")
                append("課題: ${targetAssignment.name}\n")
                append("提出ファイル: ${file.name}\n")
                append("ファイルサイズ: ${file.length()} bytes\n")
                append("アップロードID: $itemId\n")

                if (saveResult.isEmpty()) {
                    append("提出状況: 正常に保存されました\n")
                } else {
                    append("提出状況: 警告またはエラーがあります\n")
                    append("詳細: $saveResult\n")
                }

                append("\n提出が正常に完了しました。Moodleで確認してください。")
            }

            CallToolResult(content = listOf(TextContent(resultText)))

        } catch (e: Exception) {
            System.err.println(e.stackTraceToString())
            CallToolResult(
                content = listOf(TextContent("Error submitting assignment: ${e.message}\nstacktrace: ${e.stackTraceToString()}"))
            )
        }
    }

    // サーバー通信用の標準IOを使用するトランスポートを作成
    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered()
    )

    // サーバーを起動して接続を待機
    runBlocking {
        server.connect(transport)
        val done = Job()
        server.onClose {
            moodleClient.close() // リソースを解放
            done.complete()
        }
        done.join()
    }
}

/**
 * Unixタイムスタンプを日本時間の読みやすい形式に変換
 */
private fun formatUnixTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "設定なし"

    val date = java.time.Instant.ofEpochSecond(timestamp)
        .atZone(java.time.ZoneId.of("Asia/Tokyo"))

    return date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
}

/**
 * 提出状況のテキストを日本語に変換
 */
private fun getSubmissionStatusText(status: String): String {
    return when (status) {
        "new" -> "未提出"
        "draft" -> "下書き"
        "submitted" -> "提出済み"
        "reopened" -> "再開"
        else -> status
    }
}

/**
 * 採点状況のテキストを日本語に変換
 */
private fun getGradingStatusText(status: String): String {
    return when (status) {
        "notgraded" -> "未採点"
        "graded" -> "採点済み"
        "released" -> "公開済み"
        else -> status
    }
}
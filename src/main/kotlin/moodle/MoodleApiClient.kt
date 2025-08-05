package com.punyo.kotlinmcpserver.moodle

import com.punyo.kotlinmcpserver.moodle.data.AssignmentResponse
import com.punyo.kotlinmcpserver.moodle.data.Course
import com.punyo.kotlinmcpserver.moodle.data.SubmissionStatusResponse
import com.punyo.kotlinmcpserver.moodle.data.UploadedFile
import com.punyo.kotlinmcpserver.moodle.data.UserInfo
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.system.exitProcess

class MoodleApiClient {
    private val baseUrl = "https://cms7.ict.nitech.ac.jp/moodle40a/webservice/rest/server.php"
    private val uploadUrl = "https://cms7.ict.nitech.ac.jp/moodle40a/webservice/upload.php"
    private val wstoken: String
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    init {
        wstoken = System.getenv("MOODLE_TOKEN") ?: run {
            System.err.println("Error: MOODLE_TOKEN environment variable is not set")
            exitProcess(1)
        }
    }
    
    /**
     * 基本パラメータを作成
     * 
     * @param wsfunction 実行するWeb Service関数名
     * @return 基本パラメータが設定されたParametersBuilder
     */
    private fun createBaseParameters(wsfunction: String): ParametersBuilder {
        return ParametersBuilder().apply {
            append("moodlewsrestformat", "json")
            append("wsfunction", wsfunction)
            append("wstoken", wstoken)
        }
    }
    
    /**
     * 標準設定パラメータを追加
     * 
     * @param filter フィルタリング設定の有効化
     * @param fileUrl ファイルURL設定の有効化
     * @param lang 言語設定
     */
    private fun ParametersBuilder.addStandardSettings(
        filter: Boolean = true,
        fileUrl: Boolean = true,
        lang: String = "ja"
    ) {
        append("moodlewssettingfilter", filter.toString())
        append("moodlewssettingfileurl", fileUrl.toString())
        append("moodlewssettinglang", lang)
    }
    
    /**
     * API呼び出しを実行
     * 
     * @param wsfunction 実行するWeb Service関数名
     * @param configureFilter フィルタ設定を有効にするか
     * @param configureFileUrl ファイルURL設定を有効にするか
     * @param parameterBuilder 追加パラメータを設定するラムダ
     * @return APIレスポンス
     */
    private suspend inline fun <reified T> executeApiCall(
        wsfunction: String,
        configureFilter: Boolean = true,
        configureFileUrl: Boolean = true,
        noinline parameterBuilder: ParametersBuilder.() -> Unit = {}
    ): T {
        val parameters = createBaseParameters(wsfunction).apply {
            parameterBuilder()
            addStandardSettings(configureFilter, configureFileUrl)
        }
        
        val response = httpClient.submitForm(
            url = baseUrl,
            formParameters = parameters.build()
        )
        return response.body()
    }
    
    /**
     * 指定されたユーザーの登録コース一覧を取得
     * 
     * @param userId 対象ユーザーのID
     * @param returnUserCount ユーザー数を返すかどうか（0=返さない、1=返す）
     * @return コースのリスト
     */
    suspend fun getUserCourses(userId: Int, returnUserCount: Int = 0): List<Course> {
        return executeApiCall("core_enrol_get_users_courses") {
            append("userid", userId.toString())
            append("returnusercount", returnUserCount.toString())
        }
    }
    
    /**
     * 現在のユーザーの基本情報を取得
     * 
     * @return ユーザー情報（名前とユーザーID）
     */
    suspend fun getUserInfo(): UserInfo {
        return executeApiCall("core_webservice_get_site_info", configureFilter = false, configureFileUrl = false) {}
    }
    
    /**
     * 指定されたコースの課題一覧を取得
     * 
     * @param courseIds 取得対象のコースIDのリスト
     * @param includeNotEnrolledCourses 未登録コースも含めるか（1=含める、0=含めない）
     * @return 課題レスポンス（コースごとの課題一覧と警告メッセージ）
     */
    suspend fun getAssignments(courseIds: List<Int>, includeNotEnrolledCourses: Int = 1): AssignmentResponse {
        return executeApiCall("mod_assign_get_assignments") {
            courseIds.forEachIndexed { index, courseId ->
                append("courseids[$index]", courseId.toString())
            }
            append("includenotenrolledcourses", includeNotEnrolledCourses.toString())
        }
    }
    
    /**
     * 指定された課題の提出状況を取得
     * 
     * @param assignId 課題ID
     * @param userId 対象ユーザーID（省略時は認証ユーザー）
     * @return 提出状況レスポンス（提出情報、権限情報、課題データ）
     */
    suspend fun getSubmissionStatus(assignId: Int, userId: Int? = null): SubmissionStatusResponse {
        return executeApiCall("mod_assign_get_submission_status", configureFilter = false, configureFileUrl = false) {
            append("assignid", assignId.toString())
            userId?.let { append("userid", it.toString()) }
        }
    }
    
    /**
     * ファイルをMoodleサーバーにアップロード
     * 
     * @param fileContent アップロードするファイルの内容（バイト配列）
     * @param fileName ファイル名
     * @return アップロード済みファイル情報のリスト
     */
    suspend fun uploadFile(fileContent: ByteArray, fileName: String): List<UploadedFile> {
        val response = httpClient.submitFormWithBinaryData(
            url = uploadUrl,
            formData = formData {
                append("token", wstoken)
                append("filearea", "draft")
                append("itemid", "0")
                append("file", fileContent, Headers.build {
                    append(HttpHeaders.ContentType, "application/octet-stream")
                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                })
            }
        )
        
        
        val responseText = response.bodyAsText()
        
        
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        
        return json.decodeFromString<List<UploadedFile>>(responseText)
    }
    
    /**
     * 課題の提出を保存
     * 
     * @param assignmentId 課題ID
     * @param fileManagerId ファイルマネージャーのアイテムID（アップロード後に取得）
     * @return 保存結果（通常は空の配列、エラーがある場合は警告情報）
     */
    suspend fun saveSubmission(assignmentId: Int, fileManagerId: Int? = null): List<String> {
        val parameters = createBaseParameters("mod_assign_save_submission").apply {
            append("assignmentid", assignmentId.toString())
            fileManagerId?.let { append("plugindata[files_filemanager]", it.toString()) }
            addStandardSettings()
        }
        
        val response = httpClient.submitForm(
            url = baseUrl,
            formParameters = parameters.build()
        )
        val responseText = response.bodyAsText()
        return if (responseText.trim() == "[]") {
                emptyList()
            } else {
                listOf(responseText)
            }
    }
    
    /**
     * HTTPクライアントを閉じる
     * 
     * APIクライアントの使用が完了したら呼び出してリソースを解放します。
     */
    fun close() {
        httpClient.close()
    }
}
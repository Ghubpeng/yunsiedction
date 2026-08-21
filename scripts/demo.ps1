# ============================================================================
# Demo 演示数据一键播种（Stage 2.1，幂等：先清理 demo_* 数据再重建）
# 前置：应用栈已运行（scripts\up.ps1 或本地 mvn spring-boot:run），
#       且 server 已开启 YUNSIE_DEMO_ENABLED=true（.env / 环境变量）。
# 产出：demo_learner 演示学员 + 护士执业资格考试知识链 + 12 道已发布题目 +
#       1 场已发布模拟考试 + 3 门已发布课程（真实 MinIO 视频）+ 练习/进度/考试/消息数据。
# 全部经真实管理端 API 创建（与生产同一条权限链路），无任何直插数据库的业务数据。
# 用法：powershell -ExecutionPolicy Bypass -File scripts\demo.ps1 [-ApiBase http://localhost:8080]
# ============================================================================
param([string]$ApiBase = "http://localhost:8080")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$videoPath = Join-Path $PSScriptRoot "assets\demo-video.mp4"

# MySQL root 密码读取 .env（不存在则用 compose 默认值；仓库无真实密钥；忽略行内注释）
$envFile = Join-Path $root "deploy\docker\.env"
$mysqlRootPw = "devpassword"
if (Test-Path $envFile) {
    $m = Select-String -Path $envFile -Pattern '^\s*MYSQL_ROOT_PASSWORD=([^\s#]+)' | Select-Object -First 1
    if ($m) { $mysqlRootPw = $m.Matches[0].Groups[1].Value.Trim() }
}

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = "")
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    try {
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 12 -Compress
            # 显式 UTF-8 字节体：Windows PowerShell 5.1 下字符串 body 会被按系统代码页编码导致中文乱码
            $jsonBytes = [System.Text.Encoding]::UTF8.GetBytes($json)
            $r = Invoke-RestMethod -Uri "$ApiBase$Path" -Method $Method -Headers $headers `
                -ContentType "application/json" -Body $jsonBytes -TimeoutSec 30
        } else {
            $r = Invoke-RestMethod -Uri "$ApiBase$Path" -Method $Method -Headers $headers -TimeoutSec 30
        }
    } catch {
        # 原始 HTTP 错误（如 400 参数校验）：读出响应体便于定位
        $detail = $_.Exception.Message
        if ($_.Exception.Response) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $bodyText = $reader.ReadToEnd()
                if ($bodyText) { $detail = $bodyText }
            } catch { }
        }
        throw "$Method $Path HTTP 错误：$detail"
    }
    if ($r.code -ne 0) { throw "$Method $Path 失败：code=$($r.code) $($r.message)" }
    return $r.data
}

function Invoke-Sql([string]$query) {
    docker exec -e MYSQL_PWD="$mysqlRootPw" yunsie-mysql mysql -uroot -D yunsie_platform -N -e $query | Out-Null
}

function Invoke-SqlOut([string]$query) {
    $out = docker exec -e MYSQL_PWD="$mysqlRootPw" yunsie-mysql mysql -uroot -D yunsie_platform -N -e $query 2>$null
    return "$out"
}

# ---------- 1) 幂等清理上次播种残留（仅 demo_* 模式，不触碰其他数据） ----------
Write-Host "==> 清理 demo_* 残留数据"
# MinIO：先捕获 demo 课程 id，仅清理其视频目录（dev 库并存 E2E 数据时互不影响）
$demoCourseIds = Invoke-SqlOut "SELECT GROUP_CONCAT(id) FROM course_course WHERE certificate_id IN (SELECT id FROM certificate_cert WHERE code='cert-demo')"
foreach ($cid in ($demoCourseIds -split ',')) {
    if ($cid.Trim() -match '^\d+$') {
        docker exec yunsie-minio sh -c "rm -rf /data/yunsie-videos/course/$($cid.Trim())" 2>$null
    }
}
$learners = "(SELECT id FROM user_account WHERE username LIKE 'demo\_%')"
# 以证书编码为准删除业务数据（题目/考试/课程名可能为中文，名称匹配在编码问题下不可靠）
$demoCert = "(SELECT id FROM certificate_cert WHERE code='cert-demo')"
Invoke-Sql "DELETE FROM notify_message WHERE user_id IN $learners;"
Invoke-Sql "DELETE FROM learn_event_dedup;"
Invoke-Sql "DELETE FROM learn_study_calendar WHERE user_id IN $learners;"
Invoke-Sql "DELETE FROM learn_mastery WHERE user_id IN $learners;"
Invoke-Sql "DELETE FROM learn_profile_summary WHERE user_id IN $learners;"
# 演示目标：demo 证书目标 + 孤儿目标（指向已删证书）一并清理，恢复当前目标为 demo 证书
Invoke-Sql "DELETE FROM learn_user_goal WHERE certificate_id IN $demoCert OR certificate_id NOT IN (SELECT id FROM certificate_cert WHERE deleted=0);"
Invoke-Sql "DELETE FROM exam_answer WHERE attempt_id IN (SELECT id FROM exam_attempt WHERE exam_id IN (SELECT id FROM exam_exam WHERE certificate_id IN $demoCert));"
Invoke-Sql "DELETE FROM exam_attempt WHERE exam_id IN (SELECT id FROM exam_exam WHERE certificate_id IN $demoCert);"
Invoke-Sql "DELETE FROM exam_paper_option WHERE paper_question_id IN (SELECT id FROM exam_paper_question WHERE paper_id IN (SELECT id FROM exam_paper WHERE exam_id IN (SELECT id FROM exam_exam WHERE certificate_id IN $demoCert)));"
Invoke-Sql "DELETE FROM exam_paper_question WHERE paper_id IN (SELECT id FROM exam_paper WHERE exam_id IN (SELECT id FROM exam_exam WHERE certificate_id IN $demoCert));"
Invoke-Sql "DELETE FROM exam_paper WHERE exam_id IN (SELECT id FROM exam_exam WHERE certificate_id IN $demoCert);"
Invoke-Sql "DELETE FROM exam_exam WHERE certificate_id IN $demoCert;"
Invoke-Sql "DELETE FROM question_practice_record WHERE user_id IN $learners;"
Invoke-Sql "DELETE FROM question_mistake WHERE user_id IN $learners;"
Invoke-Sql "DELETE FROM question_knowledge_node WHERE question_id IN (SELECT id FROM question_question WHERE certificate_id IN $demoCert);"
Invoke-Sql "DELETE FROM question_option WHERE question_id IN (SELECT id FROM question_question WHERE certificate_id IN $demoCert);"
Invoke-Sql "DELETE FROM question_question WHERE certificate_id IN $demoCert;"
Invoke-Sql "DELETE FROM course_learn_progress WHERE lesson_id IN (SELECT id FROM course_lesson WHERE chapter_id IN (SELECT id FROM course_chapter WHERE course_id IN (SELECT id FROM course_course WHERE certificate_id IN $demoCert)));"
Invoke-Sql "DELETE FROM course_lesson_knowledge_node WHERE lesson_id IN (SELECT id FROM course_lesson WHERE chapter_id IN (SELECT id FROM course_chapter WHERE course_id IN (SELECT id FROM course_course WHERE certificate_id IN $demoCert)));"
Invoke-Sql "DELETE FROM course_lesson WHERE chapter_id IN (SELECT id FROM course_chapter WHERE course_id IN (SELECT id FROM course_course WHERE certificate_id IN $demoCert));"
Invoke-Sql "DELETE FROM course_chapter WHERE course_id IN (SELECT id FROM course_course WHERE certificate_id IN $demoCert);"
Invoke-Sql "DELETE FROM course_course WHERE certificate_id IN $demoCert;"
Invoke-Sql "DELETE FROM subject_knowledge_node WHERE code IN ('ch-demo','kp-demo');"
Invoke-Sql "DELETE FROM subject_subject WHERE code='subj-demo';"
Invoke-Sql "DELETE FROM subject_version WHERE certificate_id IN $demoCert;"
Invoke-Sql "DELETE FROM certificate_cert WHERE code='cert-demo';"
Invoke-Sql "DELETE FROM certificate_category WHERE code='cat-demo';"
Invoke-Sql "DELETE FROM sys_user_role WHERE user_id IN $learners;"
Invoke-Sql "DELETE FROM sys_role_permission WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='demo_teacher_role');"
Invoke-Sql "DELETE FROM sys_role WHERE role_code='demo_teacher_role';"
Invoke-Sql "DELETE FROM user_refresh_token WHERE user_id IN $learners;"
Invoke-Sql "DELETE FROM user_profile WHERE user_id IN $learners;"
Invoke-Sql "DELETE FROM user_credential WHERE user_id IN $learners;"
Invoke-Sql "DELETE FROM user_account WHERE username LIKE 'demo\_%';"

# ---------- 2) 管理员登录（真实鉴权链路） ----------
Write-Host "==> 管理员登录"
$admin = Invoke-Api "POST" "/api/v1/user/auth/login" @{ account = "admin"; password = "Admin@123456" }
$adminToken = $admin.accessToken

# ---------- 3) 演示账号（学员 + 教师；管理员体验复用既有 admin 账号） ----------
Write-Host "==> 创建演示账号"
Invoke-Api "POST" "/api/v1/user/accounts" @{
    username = "demo_learner"; mobile = $null; nickname = "演示学员"
    password = "Demo@123456"; userType = 1; roleIds = $null
} $adminToken | Out-Null
$demo = Invoke-Api "POST" "/api/v1/user/auth/login" @{ account = "demo_learner"; password = "Demo@123456" }
$demoToken = $demo.accessToken

Invoke-Api "POST" "/api/v1/user/accounts" @{
    username = "demo_teacher"; mobile = $null; nickname = "演示教师"
    password = "Demo@123456"; userType = 2; roleIds = $null
} $adminToken | Out-Null

# 教师角色：course 全权限 + 题目/考试只读（与真实教师 DataScope 一致）
$permTree = Invoke-Api "GET" "/api/v1/sys/permissions/tree" $null $adminToken
$wantCodes = @(
    'course:course:create', 'course:course:update', 'course:course:delete',
    'course:course:read', 'course:course:publish', 'course:video:upload',
    'question:question:read', 'exam:exam:read', 'learn:profile:view'
)
$permIds = @()
function Resolve-PermIds {
    param($nodes)
    foreach ($n in $nodes) {
        if ($wantCodes -contains $n.permissionCode) { $script:permIds += $n.id }
        if ($n.children) { Resolve-PermIds $n.children }
    }
}
Resolve-PermIds $permTree
$roleId = Invoke-Api "POST" "/api/v1/sys/roles" @{
    roleCode = "demo_teacher_role"; roleName = "演示教师角色"
    roleType = 2; status = 1; remark = ""; sort = 0
} $adminToken
Invoke-Api "POST" "/api/v1/sys/roles/$roleId/permissions" @{ permissionIds = $permIds } $adminToken | Out-Null
$teacherUserId = (Invoke-Api "POST" "/api/v1/user/auth/login" @{ account = "demo_teacher"; password = "Demo@123456" }).user.id
Invoke-Api "POST" "/api/v1/user/accounts/$teacherUserId/roles" @{ roleIds = @($roleId) } $adminToken | Out-Null

# ---------- 4) 知识链（证书/版本/科目/章/知识点） ----------
Write-Host "==> 创建知识链（护士执业资格考试）"
$categoryId = Invoke-Api "POST" "/api/v1/certificate/categories" @{
    parentId = 0; name = "护理类"; code = "cat-demo"
} $adminToken
$certId = Invoke-Api "POST" "/api/v1/certificate/certificates" @{
    categoryId = $categoryId; name = "护士执业资格考试"; code = "cert-demo"
} $adminToken
$versionId = Invoke-Api "POST" "/api/v1/subject/versions" @{
    certificateId = $certId; versionNo = "2026"; name = "2026 版"
} $adminToken
Invoke-Api "PUT" "/api/v1/subject/versions/$versionId/current" $null $adminToken | Out-Null
$subjectId = Invoke-Api "POST" "/api/v1/subject/subjects" @{
    certificateId = $certId; name = "专业实务"; code = "subj-demo"
} $adminToken
$chapterNodeId = Invoke-Api "POST" "/api/v1/subject/nodes" @{
    versionId = $versionId; subjectId = $subjectId; parentId = 0
    nodeType = 1; name = "基础护理知识与技能"; code = "ch-demo"
} $adminToken
$kpId = Invoke-Api "POST" "/api/v1/subject/nodes" @{
    versionId = $versionId; subjectId = $subjectId; parentId = $chapterNodeId
    nodeType = 2; name = "护理程序与无菌技术"; code = "kp-demo"
} $adminToken

# 演示学员建立考试目标（Stage 2.2：只选证书，科目自动关联）
Invoke-Api "POST" "/api/v1/learn/goals/select" @{
    certificateId = $certId
} $demoToken | Out-Null
# 演示教师同样建立目标（教师体验进入学习首页）
$teacherToken = (Invoke-Api "POST" "/api/v1/user/auth/login" @{ account = "demo_teacher"; password = "Demo@123456" }).accessToken
Invoke-Api "POST" "/api/v1/learn/goals/select" @{
    certificateId = $certId
} $teacherToken | Out-Null

# ---------- 5) 12 道已发布单选题（答案统一 A，保证演示考试结果可预期） ----------
Write-Host "==> 创建 12 道已发布题目"
$stems = @(
    "护士执行无菌操作时，正确的做法是", "护理程序的第一个步骤是", "测量生命体征时，正常成人安静状态下脉搏为",
    "给药三查七对中的「三查」不包括", "压疮好发于", "热疗的禁忌部位是",
    "导尿术的目的不包括", "输液中发生空气栓塞时，患者应取", "医院感染的主要传播途径不包括",
    "心肺复苏按压与通气的比例是", "留置导尿管患者的护理措施错误的是", "洗手的指征不包括"
)
foreach ($stem in $stems) {
    $qid = Invoke-Api "POST" "/api/v1/question/questions" @{
        certificateId = $certId; questionType = 1; stem = $stem
        analysis = "本题考查基础护理学核心考点，详见教材对应章节。"
        answer = "A"; difficulty = 2; source = 2
        options = @(
            @{ optionKey = "A"; content = "操作前认真洗手并保持无菌物品不被污染" },
            @{ optionKey = "B"; content = "操作后清点物品" },
            @{ optionKey = "C"; content = "操作中随意交谈" },
            @{ optionKey = "D"; content = "操作结束立即离开" }
        )
        nodeIds = @($kpId)
    } $adminToken
    Invoke-Api "POST" "/api/v1/question/questions/$qid/submit-review" @{} $adminToken | Out-Null
    Invoke-Api "POST" "/api/v1/question/questions/$qid/approve" @{} $adminToken | Out-Null
}

# ---------- 6) 已发布模拟考试 ----------
Write-Host "==> 创建并发布模拟考试"
$examId = Invoke-Api "POST" "/api/v1/exam/exams" @{
    certificateId = $certId; name = "Demo 模拟考试"; durationMinutes = 60
    passScore = 60; rule = @{ questionCount = 5; questionTypes = @(1) }
} $adminToken
Invoke-Api "POST" "/api/v1/exam/exams/$examId/assemble" @{} $adminToken | Out-Null
Invoke-Api "POST" "/api/v1/exam/exams/$examId/publish" @{} $adminToken | Out-Null

# ---------- 7) 3 门已发布课程（真实 MinIO 视频） ----------
Write-Host "==> 创建 3 门课程（含真实视频上传）"
function New-DemoCourse {
    param([string]$Title, [string]$Description, [int]$Chapters, [int]$LessonsPerChapter, [bool]$WithVideo)
    $courseId = Invoke-Api "POST" "/api/v1/course/courses" @{
        certificateId = $certId; subjectId = $subjectId; versionId = $versionId
        teacherId = $teacherUserId; title = $Title; description = $Description
    } $adminToken
    for ($c = 0; $c -lt $Chapters; $c++) {
        $chapterId = Invoke-Api "POST" "/api/v1/course/chapters" @{
            courseId = $courseId; title = "第 $($c + 1) 章"; sort = $c
        } $adminToken
        for ($l = 0; $l -lt $LessonsPerChapter; $l++) {
            $lessonId = Invoke-Api "POST" "/api/v1/course/lessons" @{
                chapterId = $chapterId; title = "第 $($c + 1) 讲 · 核心考点 $($l + 1)"
                durationSeconds = 42; sort = $l
            } $adminToken
            # 每章首讲关联知识点（章节练习题目范围 = 章节关联知识点）
            if ($l -eq 0) {
                Invoke-Api "PUT" "/api/v1/course/lessons/$lessonId/knowledge-nodes" @{ nodeIds = @($kpId) } $adminToken | Out-Null
            }
            if ($WithVideo -and $c -eq 0 -and $l -eq 0) {
                $up = & curl.exe -s -X POST "$ApiBase/api/v1/course/lessons/$lessonId/video" `
                    -H "Authorization: Bearer $adminToken" `
                    -F "file=@$videoPath;type=video/mp4"
                $u = $up | ConvertFrom-Json
                if ($u.code -ne 0) { throw "视频上传失败：$($u.message)" }
                Write-Host "   视频已上传：$Title / 第 1 讲"
            }
        }
    }
    Invoke-Api "POST" "/api/v1/course/courses/$courseId/publish" @{} $adminToken | Out-Null
    return $courseId
}

$course1 = New-DemoCourse "Demo 基础护理学" "从护理程序到无菌技术，护士执业资格考试核心科目。" 2 2 $true
$course2 = New-DemoCourse "Demo 内科护理学" "呼吸、循环、消化系统疾病护理要点精讲。" 1 2 $true
$course3 = New-DemoCourse "Demo 外科护理学" "围手术期护理与常见外科疾病护理。" 1 2 $false

# ---------- 8) 学员行为播种（真实链路：练习/进度/考试交卷 → 成绩通知/学习档案） ----------
Write-Host "==> 播种学员行为（练习/进度/考试）"
# 取真实已发布题目（mode=1 顺序模式，仅需证书过滤）：Q1 答错（生成薄弱点与错题），Q2/Q3 答对
$pq = Invoke-Api "GET" "/api/v1/question/practice/next?mode=1&certificateId=$certId&size=3" $null $demoToken
$q1 = $pq[0]; $q2 = $pq[1]; $q3 = $pq[2]
Invoke-Api "POST" "/api/v1/question/practice/submit" @{
    questionId = $q1.id; answer = "B"; mode = 3; nodeId = $kpId; responseTimeMs = 4200
} $demoToken | Out-Null
Invoke-Api "POST" "/api/v1/question/practice/submit" @{
    questionId = $q2.id; answer = "A"; mode = 3; nodeId = $kpId; responseTimeMs = 3200
} $demoToken | Out-Null
Invoke-Api "POST" "/api/v1/question/practice/submit" @{
    questionId = $q3.id; answer = "A"; mode = 3; nodeId = $kpId; responseTimeMs = 2800
} $demoToken | Out-Null

# 进度：课程 1 两讲 + 课程 2 一讲
$c1Tree = Invoke-Api "GET" "/api/v1/course/public/courses/$course1/tree" $null $demoToken
$lessonA = $c1Tree.chapters[0].lessons[0].id
$lessonB = $c1Tree.chapters[1].lessons[0].id
$c2Tree = Invoke-Api "GET" "/api/v1/course/public/courses/$course2/tree" $null $demoToken
$lessonC = $c2Tree.chapters[0].lessons[0].id
Invoke-Api "POST" "/api/v1/course/progress" @{ lessonId = $lessonA; positionSeconds = 36 } $demoToken | Out-Null
Invoke-Api "POST" "/api/v1/course/progress" @{ lessonId = $lessonB; positionSeconds = 12 } $demoToken | Out-Null
Invoke-Api "POST" "/api/v1/course/progress" @{ lessonId = $lessonC; positionSeconds = 28 } $demoToken | Out-Null

# 考试：开始 → 全部答 A → 交卷（异步生成成绩通知与学习档案）
$attempt = Invoke-Api "POST" "/api/v1/exam/exams/$examId/attempts" $null $demoToken
$answers = @()
foreach ($pq in $attempt.questions) {
    $answers += @{ paperQuestionId = $pq.paperQuestionId; answer = $pq.options[0].optionKey }
}
Invoke-Api "PUT" "/api/v1/exam/attempts/$($attempt.attemptId)/answers" @{ answers = $answers } $demoToken | Out-Null
Invoke-Api "POST" "/api/v1/exam/attempts/$($attempt.attemptId)/submit" @{} $demoToken | Out-Null

# 等待异步消费者（成绩通知 + 学习档案同步）
$ready = $false
for ($i = 0; $i -lt 30 -and -not $ready; $i++) {
    Start-Sleep -Milliseconds 500
    $unread = Invoke-Api "GET" "/api/v1/notify/messages/unread-count" $null $demoToken
    $summary = Invoke-Api "GET" "/api/v1/learn/me/summary" $null $demoToken
    $ready = ($unread -ge 1) -and ($summary.examCount -ge 1) -and ($summary.practiceCount -ge 3)
}
if (-not $ready) { throw "异步消费者未收敛（成绩通知/学习档案）" }

Write-Host ""
Write-Host "================ Demo 数据就绪 ================" -ForegroundColor Green
Write-Host "  演示学员：demo_learner / Demo@123456"
Write-Host "  内容：3 门课程（含视频）· 12 题 · 1 场模拟考试 · 练习/进度/成绩/消息"
Write-Host "  体验入口：http://localhost  →「进入 Demo」一键直达"
Write-Host "==============================================" -ForegroundColor Green

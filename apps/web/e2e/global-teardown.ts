import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

/**
 * E2E 全局清理：物理删除独立 E2E 数据（业务表 + learn/notify 派生表 + MinIO 视频对象）。
 * 仅清理 e2e_web_* 前缀与 E2E 名称数据，不触碰其他业务数据。
 */
const __dirname = dirname(fileURLToPath(import.meta.url))

function sql(query: string): void {
  execSync(`docker exec -i yunsie-mysql mysql -uroot -pdevpassword -D yunsie_platform -e "${query}"`, {
    stdio: 'ignore',
  })
}

export default function globalTeardown(): void {
  let raw: Record<string, unknown> | null = null
  try {
    raw = JSON.parse(readFileSync(join(__dirname, '.state.json'), 'utf-8'))
  } catch {
    // setup 未完成（失败）→ 无状态可清理，不阻塞
    console.log('[global-teardown] no state file, skip cleanup')
    return
  }
  const state = raw as unknown as {
    learnerId: number
    otherId: number
    examSeedId: number
    examUiId: number
    questionIds: number[]
    lesson1Id: number
    lesson2Id: number
    chapterId: number
    courseId: number
    kpId: number
    chapterNodeId: number
    subjectId: number
    versionId: number
    certId: number
    categoryId: number
    teacher1Id: number
    teacher2Id: number
    adminLearnerId: number
    roleId: number
    courseAId: number
    chapterAId: number
    lessonAId: number
    courseBId: number
  }
  try {
    const learnerIds = [state.learnerId, state.otherId, state.adminLearnerId, state.teacher1Id, state.teacher2Id]
      .filter((v) => typeof v === 'number')
      .join(',')
    const examIds = `${state.examSeedId},${state.examUiId}`
    const questionIds = state.questionIds.join(',')

    // Stage 2.1：清理必须按 e2e 用户范围过滤（dev 库并存 Demo 数据，不得清空他人档案/消息）
    sql(`DELETE FROM notify_message WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM learn_study_calendar WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM learn_mastery WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM learn_profile_summary WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM exam_answer WHERE attempt_id IN (SELECT id FROM exam_attempt WHERE exam_id IN (${examIds}));`)
    sql(`DELETE FROM exam_attempt WHERE exam_id IN (${examIds});`)
    sql(`DELETE FROM exam_paper_option WHERE paper_question_id IN (SELECT id FROM exam_paper_question WHERE paper_id IN (SELECT id FROM exam_paper WHERE exam_id IN (${examIds})));`)
    sql(`DELETE FROM exam_paper_question WHERE paper_id IN (SELECT id FROM exam_paper WHERE exam_id IN (${examIds}));`)
    sql(`DELETE FROM exam_paper WHERE exam_id IN (${examIds});`)
    sql(`DELETE FROM exam_exam WHERE id IN (${examIds});`)
    sql(`DELETE FROM question_practice_record WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM question_mistake WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM question_knowledge_node WHERE question_id IN (${questionIds});`)
    sql(`DELETE FROM question_option WHERE question_id IN (${questionIds});`)
    sql(`DELETE FROM question_question WHERE id IN (${questionIds});`)
    // 管理端 E2E 过程中创建的题目/考试（动态 id 不可预知，按名称模式清理）
    sql(`DELETE FROM question_knowledge_node WHERE question_id IN (SELECT id FROM question_question WHERE stem LIKE 'E2E Admin Q%' OR stem LIKE 'E2E Imported%');`)
    sql(`DELETE FROM question_option WHERE question_id IN (SELECT id FROM question_question WHERE stem LIKE 'E2E Admin Q%' OR stem LIKE 'E2E Imported%');`)
    sql(`DELETE FROM question_question WHERE stem LIKE 'E2E Admin Q%' OR stem LIKE 'E2E Imported%';`)
    const adminExamIds = `(SELECT id FROM exam_exam WHERE name LIKE 'E2E Admin Exam%')`
    sql(`DELETE FROM exam_answer WHERE attempt_id IN (SELECT id FROM exam_attempt WHERE exam_id IN ${adminExamIds});`)
    sql(`DELETE FROM exam_attempt WHERE exam_id IN ${adminExamIds};`)
    sql(`DELETE FROM exam_paper_option WHERE paper_question_id IN (SELECT id FROM exam_paper_question WHERE paper_id IN (SELECT id FROM exam_paper WHERE exam_id IN ${adminExamIds}));`)
    sql(`DELETE FROM exam_paper_question WHERE paper_id IN (SELECT id FROM exam_paper WHERE exam_id IN ${adminExamIds});`)
    sql(`DELETE FROM exam_paper WHERE exam_id IN ${adminExamIds};`)
    sql(`DELETE FROM exam_exam WHERE name LIKE 'E2E Admin Exam%';`)
    // Stage 2.3A 内容生命周期（LC 前缀自包含数据，按名称模式物理清理）
    sql(`DELETE FROM question_knowledge_node WHERE question_id IN (SELECT id FROM question_question WHERE stem LIKE 'LC Q%');`)
    sql(`DELETE FROM question_option WHERE question_id IN (SELECT id FROM question_question WHERE stem LIKE 'LC Q%');`)
    sql(`DELETE FROM question_question WHERE stem LIKE 'LC Q%';`)
    sql(`DELETE FROM course_course WHERE title LIKE 'LC Course%';`)
    sql(`DELETE FROM subject_knowledge_node WHERE certificate_id IN (SELECT id FROM certificate_cert WHERE code='cert-lc-e2e');`)
    sql(`DELETE FROM subject_subject WHERE certificate_id IN (SELECT id FROM certificate_cert WHERE code='cert-lc-e2e');`)
    sql(`DELETE FROM subject_version WHERE certificate_id IN (SELECT id FROM certificate_cert WHERE code='cert-lc-e2e');`)
    sql(`DELETE FROM certificate_cert WHERE code='cert-lc-e2e';`)
    sql(`DELETE FROM certificate_category WHERE code='cat-lc-e2e';`)
    // Stage 2.3B 内容运营（CO 前缀自包含数据）
    sql(`DELETE FROM question_knowledge_node WHERE question_id IN (SELECT id FROM question_question WHERE stem LIKE 'CO Q%');`)
    sql(`DELETE FROM question_option WHERE question_id IN (SELECT id FROM question_question WHERE stem LIKE 'CO Q%');`)
    sql(`DELETE FROM question_question WHERE stem LIKE 'CO Q%';`)
    sql(`DELETE FROM course_course WHERE title LIKE 'CO Course%';`)
    sql(`DELETE FROM subject_knowledge_node WHERE certificate_id IN (SELECT id FROM certificate_cert WHERE code IN ('cert-co-e2e','cert-co-copy'));`)
    sql(`DELETE FROM subject_subject WHERE certificate_id IN (SELECT id FROM certificate_cert WHERE code IN ('cert-co-e2e','cert-co-copy'));`)
    sql(`DELETE FROM subject_version WHERE certificate_id IN (SELECT id FROM certificate_cert WHERE code IN ('cert-co-e2e','cert-co-copy'));`)
    sql(`DELETE FROM certificate_cert WHERE code IN ('cert-co-e2e','cert-co-copy');`)
    sql(`DELETE FROM certificate_category WHERE code='cat-co-e2e';`)
    sql(`DELETE FROM course_learn_progress WHERE lesson_id IN (${state.lesson1Id},${state.lesson2Id},${state.lessonAId});`)
    sql(`DELETE FROM course_lesson_knowledge_node WHERE lesson_id IN (${state.lesson1Id},${state.lesson2Id},${state.lessonAId});`)
    sql(`DELETE FROM course_lesson WHERE id IN (${state.lesson1Id},${state.lesson2Id},${state.lessonAId});`)
    sql(`DELETE FROM course_chapter WHERE id IN (${state.chapterId},${state.chapterAId});`)
    sql(`DELETE FROM course_course WHERE id IN (${state.courseId},${state.courseAId},${state.courseBId});`)
    if (state.roleId) {
      sql(`DELETE FROM sys_role_permission WHERE role_id=${state.roleId};`)
      sql(`DELETE FROM sys_role WHERE id=${state.roleId};`)
    }
    sql(`DELETE FROM subject_knowledge_node WHERE id IN (${state.kpId},${state.chapterNodeId});`)
    sql(`DELETE FROM subject_subject WHERE id=${state.subjectId};`)
    sql(`DELETE FROM subject_version WHERE id=${state.versionId} OR certificate_id IN (SELECT id FROM certificate_cert WHERE code='cert-switch-e2e');`)
    sql(`DELETE FROM certificate_cert WHERE id=${state.certId} OR code='cert-admin-e2e' OR code='cert-switch-e2e';`)
    sql(`DELETE FROM certificate_category WHERE id=${state.categoryId} OR code='cat-admin-e2e' OR code='cat-switch-e2e';`)
    sql(`DELETE FROM sys_user_role WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM user_refresh_token WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM user_profile WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM user_credential WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM learn_user_goal WHERE user_id IN (${learnerIds});`)
    sql(`DELETE FROM user_account WHERE id IN (${learnerIds});`)
    // 测试过程中动态创建的用户（id 不可预知）：按前缀兜底清理
    sql(`DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM user_account WHERE username LIKE 'e2e\\_%');`)
    sql(`DELETE FROM user_refresh_token WHERE user_id IN (SELECT id FROM user_account WHERE username LIKE 'e2e\\_%');`)
    sql(`DELETE FROM user_profile WHERE user_id IN (SELECT id FROM user_account WHERE username LIKE 'e2e\\_%');`)
    sql(`DELETE FROM user_credential WHERE user_id IN (SELECT id FROM user_account WHERE username LIKE 'e2e\\_%');`)
    sql(`DELETE FROM user_account WHERE username LIKE 'e2e\\_%';`)

    // MinIO：仅清理本次 E2E 课程视频对象（Demo 课程视频不受影响）
    try {
      for (const cid of [state.courseId, state.courseAId, state.courseBId]) {
        if (typeof cid === 'number') {
          execSync(`docker exec yunsie-minio sh -c "rm -rf /data/yunsie-videos/course/${cid}"`, { stdio: 'ignore' })
        }
      }
    } catch {
      // MinIO 容器不可用时不阻塞 teardown
    }
    console.log('[global-teardown] E2E data cleaned')
  } catch (e) {
    console.error('[global-teardown] failed', e)
    process.exitCode = 1
  }
}

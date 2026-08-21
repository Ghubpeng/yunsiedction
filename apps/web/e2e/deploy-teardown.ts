import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

/**
 * 部署冒烟全局清理：物理删除 Deploy* 数据（课程/证书/分类/学员 + MinIO 视频对象），
 * 不触碰 dev E2E（e2e_*）与其他业务数据。
 */
const __dirname = dirname(fileURLToPath(import.meta.url))

function sql(query: string): void {
  execSync(`docker exec -i yunsie-mysql mysql -uroot -pdevpassword -D yunsie_platform -e "${query}"`, {
    stdio: 'ignore',
  })
}

export default function deployTeardown(): void {
  let state: { courseId: number; lessonId: number } | null = null
  try {
    state = JSON.parse(readFileSync(join(__dirname, '.deploy-state.json'), 'utf-8'))
  } catch {
    console.log('[deploy-teardown] no state file, skip cleanup')
    return
  }
  try {
    const learners = `(SELECT id FROM user_account WHERE username LIKE 'deploy\\_%')`
    sql(`DELETE FROM course_learn_progress WHERE lesson_id=${state.lessonId};`)
    sql(`DELETE FROM course_lesson_knowledge_node WHERE lesson_id=${state.lessonId};`)
    sql(`DELETE FROM course_lesson WHERE id=${state.lessonId};`)
    sql(`DELETE FROM course_chapter WHERE course_id=${state.courseId};`)
    sql(`DELETE FROM course_course WHERE id=${state.courseId};`)
    // 冒烟为 demo 账号建立的 Deploy 目标（避免污染演示体验的当前目标）
    sql(`DELETE FROM learn_user_goal WHERE certificate_id IN (SELECT id FROM certificate_cert WHERE code='cert-deploy');`)
    sql(`DELETE FROM certificate_cert WHERE code='cert-deploy';`)
    sql(`DELETE FROM certificate_category WHERE code='cat-deploy';`)
    sql(`DELETE FROM sys_user_role WHERE user_id IN ${learners};`)
    sql(`DELETE FROM user_refresh_token WHERE user_id IN ${learners};`)
    sql(`DELETE FROM user_profile WHERE user_id IN ${learners};`)
    sql(`DELETE FROM user_credential WHERE user_id IN ${learners};`)
    sql(`DELETE FROM user_account WHERE username LIKE 'deploy\\_%';`)
    // MinIO：清理部署冒烟上传的视频对象（按课程目录精确删除）
    try {
      execSync(`docker exec yunsie-minio sh -c "rm -rf /data/yunsie-videos/course/${state.courseId}"`, { stdio: 'ignore' })
    } catch {
      // MinIO 容器不可用时不阻塞 teardown
    }
    console.log('[deploy-teardown] deploy data cleaned')
  } catch (e) {
    console.error('[deploy-teardown] failed', e)
    process.exitCode = 1
  }
}

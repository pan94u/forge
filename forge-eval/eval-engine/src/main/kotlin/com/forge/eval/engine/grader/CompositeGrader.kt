package com.forge.eval.engine.grader

import com.forge.eval.protocol.*
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * 组合 grader：将多个 GraderConfig 路由到对应的 grader 实现。
 *
 * - CODE_BASED → CodeBasedGrader
 * - MODEL_BASED → ModelBasedGrader（如果可用）
 * - HUMAN → 跳过（延迟到人工评审）
 */
class CompositeGrader(
    private val codeGrader: CodeBasedGrader,
    private val modelGrader: ModelBasedGrader? = null
) {

    private val logger = LoggerFactory.getLogger(CompositeGrader::class.java)

    /**
     * 对所有 GraderConfig 执行评分，返回所有 grade 结果。
     *
     * @param trialId 试验 ID
     * @param output Agent 的输出文本
     * @param configs 所有 grader 配置
     * @param transcript 可选的交互记录
     * @return 所有 grader 产生的 grade 列表
     */
    suspend fun gradeAll(
        trialId: UUID,
        output: String,
        configs: List<GraderConfig>,
        transcript: EvalTranscript? = null
    ): List<EvalGrade> {
        val grades = mutableListOf<EvalGrade>()

        for (config in configs) {
            when (config.type) {
                GraderType.CODE_BASED -> {
                    val grade = codeGrader.grade(trialId, output, config.assertions, transcript)
                    grades.add(grade)
                }

                GraderType.MODEL_BASED -> {
                    if (modelGrader != null) {
                        val grade = modelGrader.grade(trialId, output, config, transcript)
                        grades.add(grade)
                    } else {
                        logger.warn(
                            "MODEL_BASED grader requested but no ModelAdapter available — skipping. " +
                                "Provide a ModelAdapter to enable model-based grading."
                        )
                    }
                }

                GraderType.HUMAN -> {
                    logger.info("HUMAN grader deferred for trial {} — requires manual review", trialId)
                }
            }
        }

        return grades
    }
}

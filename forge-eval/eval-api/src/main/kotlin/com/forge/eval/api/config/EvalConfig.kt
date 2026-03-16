package com.forge.eval.api.config

import com.forge.adapter.model.ModelAdapter
import com.forge.eval.engine.EvalEngine
import com.forge.eval.engine.ReportGenerator
import com.forge.eval.engine.grader.CodeBasedGrader
import com.forge.eval.engine.grader.ModelBasedGrader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EvalConfig {

    @Bean
    fun codeBasedGrader(): CodeBasedGrader = CodeBasedGrader()

    /**
     * ModelBasedGrader bean — 仅在 ModelAdapter 可用时创建。
     * ModelAdapter 由 web-ide 或其他模块注入，eval-api 本身不强依赖。
     */
    @Bean
    fun modelBasedGrader(modelAdapter: ModelAdapter?): ModelBasedGrader? =
        modelAdapter?.let { ModelBasedGrader(it) }

    @Bean
    fun evalEngine(
        codeBasedGrader: CodeBasedGrader,
        modelAdapter: ModelAdapter?
    ): EvalEngine = EvalEngine(codeBasedGrader, modelAdapter)

    @Bean
    fun reportGenerator(): ReportGenerator = ReportGenerator()
}

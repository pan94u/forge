package com.forge.eval.api.config

import com.forge.eval.engine.EvalEngine
import com.forge.eval.engine.ReportGenerator
import com.forge.eval.engine.grader.CodeBasedGrader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EvalConfig {

    @Bean
    fun codeBasedGrader(): CodeBasedGrader = CodeBasedGrader()

    @Bean
    fun evalEngine(codeBasedGrader: CodeBasedGrader): EvalEngine = EvalEngine(codeBasedGrader)

    @Bean
    fun reportGenerator(): ReportGenerator = ReportGenerator()
}

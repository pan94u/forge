package com.forge.webide

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.forge.webide", "com.forge.eval.api"])
@EntityScan(basePackages = ["com.forge.webide.entity", "com.forge.eval.api.entity"])
@EnableJpaRepositories(basePackages = ["com.forge.webide.repository", "com.forge.eval.api.repository"])
@EnableScheduling
class ForgeWebIdeApplication

fun main(args: Array<String>) {
    runApplication<ForgeWebIdeApplication>(*args)
}

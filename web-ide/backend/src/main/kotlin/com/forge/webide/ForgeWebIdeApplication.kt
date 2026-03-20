package com.forge.webide

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.forge.webide"])
@EntityScan(basePackages = ["com.forge.webide"])
@EnableJpaRepositories(basePackages = ["com.forge.webide"])
@EnableScheduling
class ForgeWebIdeApplication

fun main(args: Array<String>) {
    runApplication<ForgeWebIdeApplication>(*args)
}

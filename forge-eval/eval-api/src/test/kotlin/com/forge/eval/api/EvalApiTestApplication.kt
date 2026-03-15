package com.forge.eval.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["com.forge.eval.api"])
@EntityScan(basePackages = ["com.forge.eval.api.entity"])
@EnableJpaRepositories(basePackages = ["com.forge.eval.api.repository"])
class EvalApiTestApplication

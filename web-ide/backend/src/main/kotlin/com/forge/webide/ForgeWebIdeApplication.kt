package com.forge.webide

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ForgeWebIdeApplication

fun main(args: Array<String>) {
    runApplication<ForgeWebIdeApplication>(*args)
}

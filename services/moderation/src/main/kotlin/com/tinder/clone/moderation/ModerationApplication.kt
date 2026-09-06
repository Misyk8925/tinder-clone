package com.tinder.clone.moderation

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ModerationApplication

fun main(args: Array<String>) {
	runApplication<ModerationApplication>(*args)
}

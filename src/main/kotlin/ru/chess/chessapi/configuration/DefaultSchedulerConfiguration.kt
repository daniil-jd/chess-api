package ru.chess.chessapi.configuration

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.time.Clock

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
class DefaultSchedulerConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun taskScheduler() = ThreadPoolTaskScheduler().apply {
        poolSize = 2
        setThreadNamePrefix("match-status-")
        setRemoveOnCancelPolicy(true)
    }

    @Bean
    fun lockProvider(jdbcTemplate: JdbcTemplate) = JdbcTemplateLockProvider(jdbcTemplate)
}

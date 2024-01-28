package ru.chess.chessapi.configuration

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
class DefaultSchedulerConfiguration {
    @Bean
    fun lockProvider(jdbcTemplate: JdbcTemplate) = JdbcTemplateLockProvider(jdbcTemplate)
}

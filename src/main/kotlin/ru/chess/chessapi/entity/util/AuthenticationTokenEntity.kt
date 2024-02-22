package ru.chess.chessapi.entity.util

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import ru.chess.chessapi.entity.UserEntity
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "auth_token")
@EntityListeners(AuditingEntityListener::class)
data class AuthenticationTokenEntity(
    @Id
    var token: UUID? = null,
    @ManyToOne(optional = false)
    val user: UserEntity,
    @CreatedDate
    var createdAt: LocalDateTime? = null
) {
    @PrePersist
    fun prePersist() {
        token = UUID.randomUUID()
    }
}

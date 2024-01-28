package ru.chess.chessapi.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import javax.persistence.*

@Entity
@Table(name = "registration_token")
@EntityListeners(AuditingEntityListener::class)
data class RegistrationTokenEntity(
    @Id
    var token: UUID? = null,
    @ManyToOne(cascade = [CascadeType.MERGE])
    val user: UserEntity,
    @CreatedDate
    var createdAt: LocalDateTime? = null
) {
    @PrePersist
    fun prePersist() {
        token = UUID.randomUUID()
    }
}

package ru.chess.chessapi.entity

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.Generated
import org.hibernate.annotations.GenerationTime
import java.time.Instant
import java.util.UUID
import javax.persistence.*

@Entity
@Table(name = "chess_users_2")
data class UserEntity(
    @Id
    var id: UUID? = null,
    val username: String,
    var signature: String?,
    @Generated(value = GenerationTime.INSERT)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val serialNumber: Long? = null,
    val rating: Long,
    val isBot: Boolean = false,
    @CreationTimestamp
    var createdAt: Instant? = null
) {
    @PrePersist
    fun prePersist() {
        id = UUID.randomUUID()
    }
}

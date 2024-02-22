package ru.chess.chessapi.entity

import org.hibernate.annotations.CreationTimestamp
import ru.chess.chessapi.websocket.message.enums.SideType
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
    val serialNumber: Long? = null,
    val rating: Long,
    @CreationTimestamp
    var createdAt: Instant? = null
) {
    @PrePersist
    fun prePersist() {
        id = UUID.randomUUID()
    }
}

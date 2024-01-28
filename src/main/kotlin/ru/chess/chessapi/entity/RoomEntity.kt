package ru.chess.chessapi.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import javax.persistence.*

@Entity
@Table(name = "chess_rooms")
@EntityListeners(AuditingEntityListener::class)
data class RoomEntity(
    @Id
    var id: UUID? = null,
    @OneToOne
    @JoinColumn(name = "user_id_1", referencedColumnName = "id")
    val user1: UserEntity,
    @OneToOne
    @JoinColumn(name = "user_id_2", referencedColumnName = "id")
    var user2: UserEntity?,
    @CreatedDate
    var createdAt: LocalDateTime? = null
) {
    @PrePersist
    fun prePersist() {
        id = UUID.randomUUID()
    }
}

package ru.chess.chessapi.entity

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import javax.persistence.*

@Entity
@Table(name = "chess_message")
@EntityListeners(AuditingEntityListener::class)
data class MessageEntity(
    @Id
    var id: UUID? = null,
    @OneToOne
    val room: RoomEntity,
    @OneToOne
    val sender: UserEntity,
    val message: String,
    @CreatedDate
    var createdAt: LocalDateTime? = null
)

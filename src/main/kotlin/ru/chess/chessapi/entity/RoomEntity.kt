package ru.chess.chessapi.entity

import org.hibernate.annotations.CreationTimestamp
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import ru.chess.chessapi.websocket.message.enums.SideType
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "chess_rooms_2")
data class RoomEntity(
    @Id
    var id: UUID? = null,
    @OneToOne
    @JoinColumn(name = "user_1", referencedColumnName = "id")
    val user1: UserEntity,
    val user1Side: SideType,
    @OneToOne
    @JoinColumn(name = "user_2", referencedColumnName = "id")
    var user2: UserEntity,
    val user2Side: SideType,
    var history: String?,
    var winnerSide: SideType?,
    var winType: String?,
    @CreationTimestamp
    var createdAt: LocalDateTime? = null
) {
    @PrePersist
    fun prePersist() {
        id = UUID.randomUUID()
    }
}

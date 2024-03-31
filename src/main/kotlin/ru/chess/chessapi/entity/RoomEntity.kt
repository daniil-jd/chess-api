package ru.chess.chessapi.entity

import org.hibernate.annotations.CreationTimestamp
import ru.chess.chessapi.websocket.message.enums.FinishType
import ru.chess.chessapi.websocket.message.enums.GameType
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
    @Column(name = "user_1_side")
    @Enumerated(EnumType.STRING)
    val user1Side: SideType,
    @Column(name = "user_1_name")
    val user1Name: String,
    @OneToOne
    @JoinColumn(name = "user_2", referencedColumnName = "id")
    var user2: UserEntity,
    @Column(name = "user_2_side")
    @Enumerated(EnumType.STRING)
    val user2Side: SideType,
    @Column(name = "user_2_name")
    val user2Name: String,
    @Enumerated(EnumType.STRING)
    val gameType: GameType,
    var history: String?,
    @Enumerated(EnumType.STRING)
    var winnerSide: SideType?,
    @Enumerated(EnumType.STRING)
    var winType: FinishType?,
    @CreationTimestamp
    var createdAt: LocalDateTime? = null
) {
    @PrePersist
    fun prePersist() {
        id = UUID.randomUUID()
    }
}

package ru.chess.chessapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "chess_rooms_2")
data class RoomEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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
)

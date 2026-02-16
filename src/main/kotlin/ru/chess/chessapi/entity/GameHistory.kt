package ru.chess.chessapi.entity

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import ru.chess.chessapi.web.websocket.message.enums.GameType
import ru.chess.chessapi.web.websocket.message.enums.UserGameStatus
import java.time.Instant

@Entity
@Table(name = "game_history")
data class GameHistory(
    @EmbeddedId
    val id: GameHistoryId,

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: UserEntity,

    @MapsId("roomId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    val room: RoomEntity,

    var matchNumber: Int,

    @Enumerated(EnumType.STRING)
    var gameType: GameType,

    @Enumerated(EnumType.STRING)
    val userGameStatus: UserGameStatus,

    var favourite: Boolean,

    var points: Int = 0,

    var createdAt: Instant
)

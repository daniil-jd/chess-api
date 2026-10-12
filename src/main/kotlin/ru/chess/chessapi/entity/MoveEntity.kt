package ru.chess.chessapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import ru.chess.chessapi.web.websocket.message.enums.PromotionType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID

@Entity
@Table(name = "chess_moves")
data class MoveEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "room_id", nullable = false)
    val roomId: UUID,
    @Column(name = "move_id", nullable = false)
    val moveId: UUID,
    @Column(nullable = false)
    val ply: Int,
    @Column(name = "side_of_move", nullable = false)
    @Enumerated(EnumType.STRING)
    val sideOfMove: SideType,
    @Column(nullable = false)
    val move: String,
    @Column(name = "promotion_type")
    @Enumerated(EnumType.STRING)
    val promotionType: PromotionType? = null,
    @Column(name = "turn_server_time_ms", nullable = false)
    val turnServerTimeMs: Long
)

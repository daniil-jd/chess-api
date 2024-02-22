package ru.chess.chessapi.entity

import ru.chess.chessapi.utils.Constants.CANDIDATE_MAX_TIME_TO_LIVE
import ru.chess.chessapi.websocket.message.enums.SideType
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "user_room_candidates_2")
data class UserRoomCandidateEntity(
    @Id
    var id: UUID? = null,
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    val user: UserEntity,
    val userSide: SideType,
    val createdAt: LocalDateTime,
    var actualUntil: LocalDateTime? = null
) {
    @PrePersist
    fun prePersist() {
        id = UUID.randomUUID()
        actualUntil = createdAt.plusSeconds(CANDIDATE_MAX_TIME_TO_LIVE)
    }
}

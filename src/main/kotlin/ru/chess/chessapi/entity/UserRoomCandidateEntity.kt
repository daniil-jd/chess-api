package ru.chess.chessapi.entity

import org.springframework.data.jpa.domain.support.AuditingEntityListener
import ru.chess.chessapi.utils.Constants.CANDIDATE_MAX_TIME_TO_LIVE
import java.time.LocalDateTime
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "user_room_candidates")
@EntityListeners(AuditingEntityListener::class)
data class UserRoomCandidateEntity(
    @Id
    var id: UUID? = null,
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    val user: UserEntity,
    val createdAt: LocalDateTime,
    var actualUntil: LocalDateTime? = null
) {
    @PrePersist
    fun prePersist() {
        id = UUID.randomUUID()
        actualUntil = createdAt.plusSeconds(CANDIDATE_MAX_TIME_TO_LIVE)
    }
}

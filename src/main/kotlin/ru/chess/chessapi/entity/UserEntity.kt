package ru.chess.chessapi.entity

import ru.chess.chessapi.dto.message.enums.SideType
import java.util.UUID
import javax.persistence.*

@Entity
@Table(name = "chess_users")
data class UserEntity(
    @Id
    var id: UUID? = null,
    var username: String,
    val mail: String? = null,
    val password: String? = null,
    @Enumerated(EnumType.STRING)
    var userSide: SideType,
    var isEnabled: Boolean
) {
    @PrePersist
    fun prePersist() {
        id = UUID.randomUUID()
    }

    override fun toString(): String {
        return "(id: $id, username: '$username', userSide: $userSide, isEnabled: $isEnabled)"
    }


}

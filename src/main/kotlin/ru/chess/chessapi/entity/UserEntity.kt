package ru.chess.chessapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "chess_users_2")
data class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    var username: String,
    var signature: String?,
    @Column(name = "serial_number", insertable = false, updatable = false)
    val serialNumber: Long? = null,
    var totalPoints: Long,
    val isBot: Boolean = false,
    var authCode: String? = null,
    @CreationTimestamp
    var createdAt: Instant? = null
)

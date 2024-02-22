package ru.chess.chessapi.entity.util

import java.util.UUID
import javax.persistence.*

@Entity
@Table(name = "customer")
data class CustomerEntity(
    @Id
    var id: UUID? = null,
    val username: String,
    @OneToMany(fetch = FetchType.LAZY)
    val takenList: List<CustomerEntity>
) {
    @PrePersist
    fun prePersist() {
        id = UUID.randomUUID()
    }
}
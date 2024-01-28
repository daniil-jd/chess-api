package ru.chess.chessapi.service

import org.springframework.stereotype.Service
import ru.chess.chessapi.dto.message.enums.SideType

@Service
class SideService {
    fun findOppositeSide(side: SideType): SideType {
        return when (side) {
            SideType.WHITE -> {
                SideType.BLACK
            }

            SideType.BLACK -> {
                SideType.WHITE
            }

            SideType.RANDOM -> {
                SideType.WHITE
            }
        }
    }

}
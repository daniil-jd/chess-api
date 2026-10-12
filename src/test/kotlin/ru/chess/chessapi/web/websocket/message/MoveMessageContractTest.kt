package ru.chess.chessapi.web.websocket.message

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.socket.TextMessage
import ru.chess.chessapi.service.MessageConvertorService
import ru.chess.chessapi.web.websocket.message.enums.FinishType
import ru.chess.chessapi.web.websocket.message.enums.MessageType
import ru.chess.chessapi.web.websocket.message.enums.PromotionType
import ru.chess.chessapi.web.websocket.message.enums.SideType
import java.util.UUID

class MoveMessageContractTest {
    private val mapper = jacksonObjectMapper()
    private val room = UUID.fromString("96430804-58b5-4070-a279-1cbf9ede4644")
    private val moveId = UUID.fromString("f3214004-f946-44ce-b3d1-b84e60dfba94")

    @Test
    fun `parse move from diagram and string ply sent by Unity`() {
        val json = """{
            "messageType": "CHESS_MOVE",
            "room": "$room",
            "sideOfMove": "WHITE",
            "move": "A7A8",
            "moveForHistory": "a8=Q",
            "moveId": "$moveId",
            "ply": "1",
            "promotionType": "QUEEN"
        }"""

        val message = MessageConvertorService(mapper).convertMessageDtoFromTextMessage(TextMessage(json))
                as MoveMessageDto

        assertEquals(moveId, message.moveId)
        assertEquals(1, message.ply)
        assertEquals(PromotionType.QUEEN, message.promotionType)
        assertEquals("a8=Q", message.moveForHistory)
        assertNull(message.backendUserId)
    }

    @Test
    fun `accept omitted promotion and reject moves without identity or ply`() {
        val json = """{
            "messageType": "CHESS_MOVE", "room": "$room", "sideOfMove": "WHITE",
            "move": "E2E4", "moveId": "$moveId", "ply": 1
        }"""

        assertNull(mapper.readValue<MoveMessageDto>(json).promotionType)
        assertThrows(JsonProcessingException::class.java) {
            mapper.readValue<MoveMessageDto>(json.replace("\"moveId\": \"$moveId\",", ""))
        }
        assertThrows(JsonProcessingException::class.java) {
            mapper.readValue<MoveMessageDto>(json.replace(", \"ply\": 1", ""))
        }
    }

    @Test
    fun `retry accepts Unity roomId and diagram room alias without sessionId`() {
        for (field in listOf("roomId", "room")) {
            val message = mapper.readValue<RequestForWsRetryDto>(
                """{"messageType":"WS_RETRY", "backendUserId":"$moveId", "$field":"$room"}"""
            )
            assertEquals(room, message.roomId)
            assertNull(message.sessionId)
        }
    }

    @Test
    fun `serialize complete replay with numeric server timestamps and accepted moves`() {
        val turn = MoveAcceptedMessageDto(
            room = room,
            sideOfMove = SideType.WHITE,
            move = "E2E4",
            moveId = moveId,
            ply = 1,
            turnServerTimeMs = 1_800_000_000_100L
        )
        val reply = NewWsSession(
            messageType = MessageType.NEW_WS,
            backendUserId = moveId,
            sessionId = "new-session",
            room = room,
            turns = listOf(turn),
            matchServerTimeMs = 1_800_000_000_000L
        )

        val json = mapper.readTree(mapper.writeValueAsString(reply))

        assertEquals("NEW_WS", json["messageType"].asText())
        assertEquals(room.toString(), json["room"].asText())
        assertEquals(1_800_000_000_000L, json["matchServerTimeMs"].longValue())
        assertEquals("CHESS_MOVE_ACCEPTED", json["turns"][0]["messageType"].asText())
        assertEquals(moveId.toString(), json["turns"][0]["moveId"].asText())
        assertEquals("1", json["turns"][0]["ply"].textValue())
        assertEquals(1_800_000_000_100L, json["turns"][0]["turnServerTimeMs"].longValue())
        assertEquals(reply, mapper.readValue<NewWsSession>(json.toString()))
    }

    @Test
    fun `finish message permits a draw without winner`() {
        val message = mapper.readValue<MatchFinishedMessageDto>(
            """{"messageType":"MATCH_FINISHED", "room":"$room", "finishType":"DRAW"}"""
        )
        assertNull(message.winnerSide)
        assertEquals(FinishType.DRAW, message.finishType)
    }

    @Test
    fun `converter accepts blank Unity draw winner but rejects unknown or malformed side`() {
        val converter = MessageConvertorService(mapper)
        for (winner in listOf("", "  ")) {
            val message = converter.convertMessageDtoFromTextMessage(TextMessage(
                """{"messageType":"MATCH_FINISHED", "room":"$room", "winnerSide":"$winner", "finishType":"DRAW"}"""
            )) as MatchFinishedMessageDto
            assertNull(message.winnerSide)
            assertEquals(FinishType.DRAW, message.finishType)
        }
        for (winner in listOf("\"INVALID\"", "{}")) {
            assertNull(converter.convertMessageDtoFromTextMessage(TextMessage(
                """{"messageType":"MATCH_FINISHED", "room":"$room", "winnerSide":$winner, "finishType":"DRAW"}"""
            )))
        }
    }
}

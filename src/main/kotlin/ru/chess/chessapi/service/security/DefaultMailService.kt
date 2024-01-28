package ru.chess.chessapi.service.security

import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import javax.mail.MessagingException

@Service
class DefaultMailService(
    private val mailService: JavaMailSender
) {
    private val MAIL_SENDER = "helper.spprt.616@gmail.com"
    private val MAIL_SUBJECT = "Registration in super-chess-app"
    private val CONTENT_LINK = "<a href='http://localhost:3000/registration/confirmation?token=%s&username=%s'>Link must be here</a>"

    fun sendRegistrationToken(mail: String, token: String) {
        sendMimeMessage(to = mail, text = String.format(CONTENT_LINK, mail, token))
    }

    fun sendMimeMessage(to: String, text: String) {
        try {
            val message = mailService.createMimeMessage()
            val helper = MimeMessageHelper(message)
            helper.setFrom(MAIL_SENDER)
            helper.setTo(to)
            helper.setSubject(MAIL_SUBJECT)
            helper.setText(text)

            mailService.send(message)
        } catch (ex: MessagingException) {
            throw RuntimeException("error while sending mail message")
        }
    }
}
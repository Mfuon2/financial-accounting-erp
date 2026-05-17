package com.qesuite.accounting.email

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Sends transactional emails: password reset, receipt delivery, and user invites.
 *
 * Set MAIL_ENABLED=true and configure MAIL_HOST/PORT/USERNAME/PASSWORD env vars to activate.
 * When disabled (default) all calls are no-ops — the log entry confirms the intent.
 */
@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.email.enabled:false}") private val enabled: Boolean,
    @Value("\${app.email.from:noreply@qesuite.com}") private val fromAddress: String,
    @Value("\${app.email.base-url:http://localhost:5173}") private val baseUrl: String,
) {

    private val log = LoggerFactory.getLogger(EmailService::class.java)

    @Async("auditExecutor")
    fun sendPasswordReset(toEmail: String, plaintextToken: String) {
        if (!enabled) {
            log.info("email.disabled — would send password-reset to={}", toEmail)
            return
        }
        val link = "$baseUrl/reset-password?token=$plaintextToken"
        val html = """
            <div style="font-family:sans-serif;max-width:520px">
              <h2 style="color:#111">Reset your password</h2>
              <p>Click the button below to set a new password. This link expires in 1 hour.</p>
              <a href="$link" style="display:inline-block;padding:10px 20px;background:#5B60F0;color:#fff;border-radius:6px;text-decoration:none;font-weight:600">Reset password</a>
              <p style="color:#666;font-size:12px;margin-top:24px">If you didn't request this, you can safely ignore this email.</p>
            </div>
        """.trimIndent()
        send(toEmail, "Reset your QeSuite password", html)
    }

    @Async("auditExecutor")
    fun sendReceiptDelivery(toEmail: String, receiptNumber: String, amountFormatted: String, orgName: String) {
        if (!enabled) {
            log.info("email.disabled — would send receipt={} to={}", receiptNumber, toEmail)
            return
        }
        val html = """
            <div style="font-family:sans-serif;max-width:520px">
              <h2 style="color:#111">Payment Receipt</h2>
              <p>Thank you for your payment. Please find your receipt below.</p>
              <table style="width:100%;border-collapse:collapse;margin:16px 0">
                <tr><td style="padding:8px;border-bottom:1px solid #eee;color:#666">Receipt No.</td><td style="padding:8px;border-bottom:1px solid #eee;font-weight:600">$receiptNumber</td></tr>
                <tr><td style="padding:8px;color:#666">Amount</td><td style="padding:8px;font-weight:600">$amountFormatted</td></tr>
              </table>
              <p style="color:#666;font-size:12px">Issued by $orgName · Powered by QeSuite IFRS</p>
            </div>
        """.trimIndent()
        send(toEmail, "Payment receipt $receiptNumber — $orgName", html)
    }

    @Async("auditExecutor")
    fun sendUserInvite(toEmail: String, fullName: String, inviterName: String, orgName: String, tempPassword: String) {
        if (!enabled) {
            log.info("email.disabled — would send invite to={}", toEmail)
            return
        }
        val loginLink = "$baseUrl/login"
        val html = """
            <div style="font-family:sans-serif;max-width:520px">
              <h2 style="color:#111">You've been invited to $orgName</h2>
              <p>Hi $fullName, $inviterName has invited you to QeSuite IFRS.</p>
              <table style="width:100%;border-collapse:collapse;margin:16px 0">
                <tr><td style="padding:8px;border-bottom:1px solid #eee;color:#666">Email</td><td style="padding:8px">$toEmail</td></tr>
                <tr><td style="padding:8px;color:#666">Temp password</td><td style="padding:8px;font-family:monospace;font-weight:600">$tempPassword</td></tr>
              </table>
              <a href="$loginLink" style="display:inline-block;padding:10px 20px;background:#5B60F0;color:#fff;border-radius:6px;text-decoration:none;font-weight:600">Sign in now</a>
              <p style="color:#666;font-size:12px;margin-top:16px">Please change your password on first login.</p>
            </div>
        """.trimIndent()
        send(toEmail, "You're invited to $orgName on QeSuite", html)
    }

    private fun send(to: String, subject: String, html: String) {
        try {
            val msg = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(msg, false, "UTF-8")
            helper.setFrom(fromAddress)
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(html, true)
            mailSender.send(msg)
            log.info("email.sent to={} subject={}", to, subject)
        } catch (ex: Exception) {
            log.error("email.send-failed to={} subject={} error={}", to, subject, ex.message)
        }
    }
}

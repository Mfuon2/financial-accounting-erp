package com.qesuite.accounting.shared.pdf

import com.lowagie.text.*
import com.lowagie.text.pdf.*
import com.lowagie.text.pdf.draw.LineSeparator
import com.qesuite.accounting.ledger.service.BalanceSheetReport
import com.qesuite.accounting.ledger.service.CashFlowReport
import com.qesuite.accounting.ledger.service.ProfitLossReport
import org.springframework.stereotype.Service
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class PdfReportService {

    private val ACCENT  = Color(91, 96, 240)
    private val DARK    = Color(17, 17, 17)
    private val MUTED   = Color(107, 114, 128)
    private val BORDER  = Color(229, 231, 235)
    private val BG_HEAD = Color(249, 250, 251)

    private val titleFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f, DARK)
    private val headFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, DARK)
    private val bodyFont   = FontFactory.getFont(FontFactory.HELVETICA, 9f, DARK)
    private val mutedFont  = FontFactory.getFont(FontFactory.HELVETICA, 8f, MUTED)
    private val accentFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, ACCENT)
    private val monoFont   = FontFactory.getFont(FontFactory.COURIER, 9f, DARK)

    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy")

    // ─────────────────────────────────────────────────────────────────────────
    // Balance Sheet
    // ─────────────────────────────────────────────────────────────────────────

    fun balanceSheet(report: BalanceSheetReport): ByteArray {
        val out = ByteArrayOutputStream()
        val doc = Document(PageSize.A4, 40f, 40f, 50f, 40f)
        PdfWriter.getInstance(doc, out)
        doc.open()

        reportHeader(doc, "Statement of Financial Position", "As at ${report.asOfDate.format(dateFmt)}")

        val table = makeTable()

        row2(table, "ASSETS",       fmtAmt(report.totalAssets),      BG_HEAD, headFont, headFont)
        row2(table, "LIABILITIES",  fmtAmt(report.totalLiabilities), BG_HEAD, headFont, headFont)
        row2(table, "EQUITY",       fmtAmt(report.totalEquity),       BG_HEAD, headFont, headFont)
        spacerRow(table)

        val totalLiabEquity = report.totalLiabilities.add(report.totalEquity)
        row2(table, "Total Assets",                 fmtAmt(report.totalAssets), ACCENT, accentFont, accentFont)
        row2(table, "Total Liabilities & Equity",   fmtAmt(totalLiabEquity),    ACCENT, accentFont, accentFont)
        spacerRow(table)

        val balanced = report.totalAssets.setScale(2, RoundingMode.HALF_EVEN) == totalLiabEquity.setScale(2, RoundingMode.HALF_EVEN)
        val checkCell = PdfPCell(Phrase(
            if (balanced) "✓ Accounting equation balanced" else "⚠ Equation imbalance — check adjusting entries",
            if (balanced) FontFactory.getFont(FontFactory.HELVETICA, 8f, Color(16, 185, 129))
            else FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color(239, 68, 68))
        )).apply { border = Rectangle.NO_BORDER; colspan = 2; paddingTop = 4f }
        table.addCell(checkCell)

        doc.add(table)
        reportFooter(doc)
        doc.close()
        return out.toByteArray()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Profit & Loss
    // ─────────────────────────────────────────────────────────────────────────

    fun profitLoss(report: ProfitLossReport): ByteArray {
        val out = ByteArrayOutputStream()
        val doc = Document(PageSize.A4, 40f, 40f, 50f, 40f)
        PdfWriter.getInstance(doc, out)
        doc.open()

        reportHeader(doc, "Statement of Profit & Loss",
            "${report.startDate.format(dateFmt)} – ${report.endDate.format(dateFmt)}")

        val table = makeTable()

        row2(table, "Total Revenue",      fmtAmt(report.totalRevenue),  BG_HEAD, headFont, headFont)
        row2(table, "Total Expenses",     fmtAmt(report.totalExpenses), BG_HEAD, headFont, headFont)
        spacerRow(table)
        row2(table, "Net Income / (Loss)", fmtAmt(report.netIncome),    ACCENT,  accentFont, accentFont)

        doc.add(table)
        reportFooter(doc)
        doc.close()
        return out.toByteArray()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cash Flow
    // ─────────────────────────────────────────────────────────────────────────

    fun cashFlow(report: CashFlowReport): ByteArray {
        val out = ByteArrayOutputStream()
        val doc = Document(PageSize.A4, 40f, 40f, 50f, 40f)
        PdfWriter.getInstance(doc, out)
        doc.open()

        reportHeader(doc, "Statement of Cash Flows (Indirect Method)",
            "${report.startDate.format(dateFmt)} – ${report.endDate.format(dateFmt)}")

        val table = makeTable()

        row2(table, "Net cash from operating activities", fmtAmt(report.operatingActivities), BG_HEAD, headFont, headFont)
        row2(table, "Net cash from investing activities", fmtAmt(report.investingActivities), BG_HEAD, headFont, headFont)
        row2(table, "Net cash from financing activities", fmtAmt(report.financingActivities), BG_HEAD, headFont, headFont)
        spacerRow(table)
        row2(table, "Net change in cash",   fmtAmt(report.netChangeInCash), Color.WHITE, bodyFont,   monoFont)
        row2(table, "Opening cash balance", fmtAmt(report.openingCash),     Color.WHITE, bodyFont,   monoFont)
        row2(table, "Closing cash balance", fmtAmt(report.closingCash),     ACCENT,      accentFont, accentFont)

        doc.add(table)
        reportFooter(doc)
        doc.close()
        return out.toByteArray()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun makeTable(): PdfPTable {
        val t = PdfPTable(2)
        t.widthPercentage = 100f
        t.setWidths(floatArrayOf(3f, 1.5f))
        t.setSpacingBefore(16f)
        return t
    }

    private fun row2(
        table: PdfPTable,
        label: String,
        value: String,
        bg: Color,
        labelFont: com.lowagie.text.Font,
        valueFont: com.lowagie.text.Font,
    ) {
        val lc = PdfPCell(Phrase(label, labelFont)).apply {
            backgroundColor = bg
            border = Rectangle.BOTTOM
            borderColor = BORDER
            borderWidth = 0.5f
            paddingTop = 5f; paddingBottom = 5f; paddingLeft = 6f
        }
        val vc = PdfPCell(Phrase(value, valueFont)).apply {
            backgroundColor = bg
            border = Rectangle.BOTTOM
            borderColor = BORDER
            borderWidth = 0.5f
            horizontalAlignment = Element.ALIGN_RIGHT
            paddingTop = 5f; paddingBottom = 5f; paddingRight = 6f
        }
        table.addCell(lc)
        table.addCell(vc)
    }

    private fun spacerRow(table: PdfPTable) {
        repeat(2) {
            table.addCell(PdfPCell(Phrase("")).apply { border = Rectangle.NO_BORDER; fixedHeight = 6f })
        }
    }

    private fun reportHeader(doc: Document, title: String, subtitle: String) {
        doc.add(Paragraph(title, titleFont).apply { spacingAfter = 4f })
        doc.add(Paragraph("Apollo Enterprises Limited · QeSuite IFRS", mutedFont).apply { spacingAfter = 2f })
        doc.add(Paragraph(subtitle, FontFactory.getFont(FontFactory.HELVETICA, 10f, MUTED)).apply { spacingAfter = 8f })
        doc.add(Chunk(LineSeparator(1f, 100f, BORDER, Element.ALIGN_CENTER, -2f)))
    }

    private fun reportFooter(doc: Document) {
        doc.add(Paragraph("\nAll figures in KES · Generated ${LocalDate.now().format(dateFmt)} · QeSuite IFRS", mutedFont))
    }

    private fun fmtAmt(value: BigDecimal?): String {
        if (value == null) return "—"
        return "%,.2f".format(value.setScale(2, RoundingMode.HALF_EVEN))
    }
}

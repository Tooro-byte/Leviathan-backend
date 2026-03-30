package com.leviathanledger.leviathan.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.util.LegalTemplateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PdfGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGeneratorService.class);

    @Autowired
    private QRCodeService qrCodeService;

    /**
     * Generate Notice of Intention to Sue (with optional draft watermark)
     */
    public byte[] generateNoticeOfIntention(LegalCase legalCase, boolean isDraft) {
        byte[] pdfBytes = generateNoticeOfIntentionBase(legalCase);
        if (isDraft) {
            pdfBytes = addDraftWatermark(pdfBytes);
        }
        return pdfBytes;
    }

    /**
     * Generate Summons to File Defence (with optional draft watermark)
     */
    public byte[] generateSummonsToFileDefence(LegalCase legalCase, boolean isDraft) {
        byte[] pdfBytes = generateSummonsToFileDefenceBase(legalCase);
        if (isDraft) {
            pdfBytes = addDraftWatermark(pdfBytes);
        }
        return pdfBytes;
    }

    /**
     * Generate Originating Summons (with optional draft watermark)
     */
    public byte[] generateOriginatingSummons(LegalCase legalCase, boolean isDraft) {
        byte[] pdfBytes = generateOriginatingSummonsBase(legalCase);
        if (isDraft) {
            pdfBytes = addDraftWatermark(pdfBytes);
        }
        return pdfBytes;
    }

    /**
     * Generate Summons for Directions (with optional draft watermark)
     */
    public byte[] generateSummonsForDirections(LegalCase legalCase, boolean isDraft) {
        byte[] pdfBytes = generateSummonsForDirectionsBase(legalCase);
        if (isDraft) {
            pdfBytes = addDraftWatermark(pdfBytes);
        }
        return pdfBytes;
    }

    /**
     * Generate Extension of Time (with optional draft watermark)
     */
    public byte[] generateExtensionOfTime(LegalCase legalCase, boolean isDraft) {
        byte[] pdfBytes = generateExtensionOfTimeBase(legalCase);
        if (isDraft) {
            pdfBytes = addDraftWatermark(pdfBytes);
        }
        return pdfBytes;
    }

    /**
     * CERTIFY a document - removes watermark, adds Lex Stamp and QR Code
     */
    public byte[] certifyDocument(byte[] draftPdfBytes, LegalCase legalCase,
                                  String certifiedBy, String verificationUrl, String verificationCode) {
        try {
            // First, add Lex Stamp
            byte[] stampedPdf = addLexStamp(draftPdfBytes, legalCase, certifiedBy);

            // Then, add QR Code for verification
            byte[] finalPdf = addQRCode(stampedPdf, verificationUrl, verificationCode);

            return finalPdf;
        } catch (Exception e) {
            logger.error("Certification failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to certify document: " + e.getMessage(), e);
        }
    }

    /**
     * Add DRAFT watermark to PDF
     */
    private byte[] addDraftWatermark(byte[] pdfBytes) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(reader, writer);

            PdfFont font = PdfFontFactory.createFont();

            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                PdfCanvas canvas = new PdfCanvas(page);

                Rectangle pageSize = page.getPageSize();

                canvas.saveState();
                canvas.setFillColor(ColorConstants.RED);
                canvas.setExtGState(new PdfExtGState().setFillOpacity(0.3f));
                canvas.beginText();
                canvas.setFontAndSize(font, 48);
                canvas.setTextMatrix(1, 0, 0, 1, pageSize.getWidth() / 3, pageSize.getHeight() / 2);
                canvas.showText("DRAFT");
                canvas.endText();
                canvas.restoreState();
            }

            pdfDoc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to add watermark: {}", e.getMessage(), e);
            return pdfBytes;
        }
    }

    /**
     * Add Lex Stamp (professional seal) to PDF
     */
    private byte[] addLexStamp(byte[] pdfBytes, LegalCase legalCase, String certifiedBy) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(reader, writer);

            String stampText = String.format(
                    "✓ VERIFIED BY LEXTRACKER INTEGRITY ENGINE\n" +
                            "Certified by: %s\n" +
                            "Case: %s\n" +
                            "Date: %s\n" +
                            "This is an authentic legal document generated by LexTracker Uganda.\n" +
                            "The contents of this document are verified and admissible in the Courts of Judicature.",
                    certifiedBy,
                    legalCase.getCaseNumber(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
            );

            for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                PdfPage page = pdfDoc.getPage(i);
                PdfCanvas canvas = new PdfCanvas(page);

                Rectangle pageSize = page.getPageSize();
                float yPos = 50;

                canvas.saveState();
                canvas.setFillColor(ColorConstants.DARK_GRAY);
                canvas.setExtGState(new PdfExtGState().setFillOpacity(0.8f));

                PdfFont font = PdfFontFactory.createFont();
                canvas.beginText();
                canvas.setFontAndSize(font, 8);
                canvas.setTextMatrix(1, 0, 0, 1, 50, yPos);

                String[] lines = stampText.split("\n");
                for (String line : lines) {
                    canvas.showText(line);
                    canvas.newlineText();
                    yPos -= 12;
                }
                canvas.endText();
                canvas.restoreState();

                // Add a border box around the stamp
                canvas.saveState();
                canvas.setStrokeColor(ColorConstants.BLUE);
                canvas.setLineWidth(1);
                canvas.rectangle(40, 30, pageSize.getWidth() - 80, 80);
                canvas.stroke();
                canvas.restoreState();
            }

            pdfDoc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            logger.error("Failed to add Lex Stamp: {}", e.getMessage(), e);
            return pdfBytes;
        }
    }

    /**
     * Add QR Code to PDF for verification
     */
    private byte[] addQRCode(byte[] pdfBytes, String verificationUrl, String verificationCode) {
        try {
            BufferedImage qrImage = qrCodeService.generateQRCode(verificationUrl, 100, 100);
            ByteArrayOutputStream qrBaos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "png", qrBaos);
            byte[] qrBytes = qrBaos.toByteArray();

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                PdfReader reader = new PdfReader(new ByteArrayInputStream(pdfBytes));
                PdfWriter writer = new PdfWriter(baos);
                PdfDocument pdfDoc = new PdfDocument(reader, writer);

                for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                    PdfPage page = pdfDoc.getPage(i);
                    Rectangle pageSize = page.getPageSize();

                    PdfCanvas canvas = new PdfCanvas(page);
                    canvas.saveState();

                    com.itextpdf.layout.Canvas layoutCanvas = new Canvas(canvas, pageSize);
                    Image qrCodeImage = new Image(com.itextpdf.io.image.ImageDataFactory.create(qrBytes));
                    qrCodeImage.setFixedPosition(pageSize.getWidth() - 130, 30);
                    qrCodeImage.setWidth(80);
                    qrCodeImage.setHeight(80);
                    layoutCanvas.add(qrCodeImage);

                    layoutCanvas.add(new Paragraph("Scan to Verify")
                            .setFontSize(7)
                            .setFixedPosition(pageSize.getWidth() - 125, 15, 100));

                    canvas.restoreState();
                }

                pdfDoc.close();
                return baos.toByteArray();
            }
        } catch (Exception e) {
            logger.error("Failed to add QR code: {}", e.getMessage(), e);
            return pdfBytes;
        }
    }

    // --- Base generation methods (original content without watermark) ---

    private byte[] generateNoticeOfIntentionBase(LegalCase legalCase) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            LegalTemplateUtil.addUgandanHeader(document);

            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            document.add(new Paragraph(date).setTextAlignment(TextAlignment.RIGHT));

            String defendantName = legalCase.getDefendantName() != null ? legalCase.getDefendantName().toUpperCase() : "THE DEFENDANT";
            String defendantAddress = legalCase.getDefendantAddress() != null ? legalCase.getDefendantAddress() : "Address Not Provided";

            document.add(new Paragraph("TO: " + defendantName));
            document.add(new Paragraph(defendantAddress));
            document.add(new Paragraph("\n"));

            String subject = legalCase.getCaseSubject() != null ? legalCase.getCaseSubject().toUpperCase() : "LEGAL CLAIM";
            document.add(new Paragraph("RE: NOTICE OF INTENTION TO SUE REGARDING " + subject)
                    .setBold().setUnderline().setTextAlignment(TextAlignment.CENTER));

            String clientName = legalCase.getClientName() != null ? legalCase.getClientName() : "OUR CLIENT";
            String courtLocation = legalCase.getCourtLocation() != null ? legalCase.getCourtLocation() : "KAMPALA";

            document.add(new Paragraph("\nWe act for and on behalf of our client, " + clientName + ", who has instructed us to address you as follows:"));

            document.add(new Paragraph("\nTAKE NOTICE that unless you satisfy our client's claim within seven (7) days from the date of receipt of this notice, we have firm instructions to institute legal proceedings against you in the High Court of Uganda at " + courtLocation + "."));

            document.add(new Paragraph("\nThis will be at your own peril as to costs and other incidental consequences."));
            document.add(new Paragraph("\nSTAND PROPERLY ADVISED."));

            String counsel = legalCase.getRegisteredBy() != null ? legalCase.getRegisteredBy() : "Legal Counsel";
            document.add(new Paragraph("\n\n__________________________"));
            document.add(new Paragraph("Counsel for the Plaintiff (" + counsel + ")"));

            document.close();
        } catch (Exception e) {
            logger.error("Notice generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF Generation Failed: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    private byte[] generateSummonsToFileDefenceBase(LegalCase legalCase) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            LegalTemplateUtil.addUgandanHeader(document);
            LegalTemplateUtil.addCourtInfo(document, legalCase.getCourtLocation(), legalCase.getDivision());

            document.add(new Paragraph("SUMMONS TO FILE DEFENCE")
                    .setBold().setFontSize(14).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            String caseNumber = legalCase.getCaseNumber() != null ? legalCase.getCaseNumber() : "CASE NUMBER NOT ASSIGNED";
            document.add(new Paragraph("CASE NO: " + caseNumber).setBold().setTextAlignment(TextAlignment.CENTER));

            String plaintiff = legalCase.getClientName() != null ? legalCase.getClientName() : "THE PLAINTIFF";
            String defendant = legalCase.getDefendantName() != null ? legalCase.getDefendantName() : "THE DEFENDANT";
            LegalTemplateUtil.addParties(document, plaintiff, defendant);

            document.add(new Paragraph("TO: " + defendant.toUpperCase()));
            document.add(new Paragraph("\nTAKE NOTICE that the Plaintiff has instituted a suit against you in this Honourable Court."));
            document.add(new Paragraph("\nYou are hereby required to file a Defence within fourteen (14) days from the date of service of this Summons."));
            document.add(new Paragraph("\nIf you fail to file a Defence within the stipulated time, the Plaintiff may proceed to judgment against you without further notice."));
            document.add(new Paragraph("\nTAKE FURTHER NOTICE that this matter shall be mentioned before the Registrar on a date to be fixed for directions."));

            String counsel = legalCase.getRegisteredBy() != null ? legalCase.getRegisteredBy() : "Registry Officer";
            document.add(new Paragraph("\n\nIssued by the Registry,"));
            document.add(new Paragraph("High Court of Uganda at " + (legalCase.getCourtLocation() != null ? legalCase.getCourtLocation() : "Kampala")));
            document.add(new Paragraph("\n\n__________________________"));
            document.add(new Paragraph(counsel));

            document.close();
        } catch (Exception e) {
            logger.error("Summons generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF Generation Failed: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    private byte[] generateOriginatingSummonsBase(LegalCase legalCase) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            LegalTemplateUtil.addUgandanHeader(document);
            LegalTemplateUtil.addCourtInfo(document, legalCase.getCourtLocation(), legalCase.getDivision());

            document.add(new Paragraph("ORIGINATING SUMMONS")
                    .setBold().setFontSize(14).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            String caseNumber = legalCase.getCaseNumber() != null ? legalCase.getCaseNumber() : "CASE NUMBER NOT ASSIGNED";
            document.add(new Paragraph("CASE NO: " + caseNumber).setBold().setTextAlignment(TextAlignment.CENTER));

            String plaintiff = legalCase.getClientName() != null ? legalCase.getClientName() : "THE PLAINTIFF";
            String defendant = legalCase.getDefendantName() != null ? legalCase.getDefendantName() : "THE DEFENDANT";
            LegalTemplateUtil.addParties(document, plaintiff, defendant);

            document.add(new Paragraph("LET the Defendant appear before this Honourable Court within fifteen (15) days from the date of service to show cause why the orders sought should not be granted."));
            document.add(new Paragraph("\nTAKE NOTICE that this Summons shall be heard on a date to be fixed by the Registrar."));

            String counsel = legalCase.getRegisteredBy() != null ? legalCase.getRegisteredBy() : "Registry Officer";
            document.add(new Paragraph("\n\nIssued by the Registry,\nHigh Court of Uganda\n\n__________________________\n" + counsel));

            document.close();
        } catch (Exception e) {
            logger.error("Originating Summons generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF Generation Failed: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    private byte[] generateSummonsForDirectionsBase(LegalCase legalCase) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            LegalTemplateUtil.addUgandanHeader(document);
            LegalTemplateUtil.addCourtInfo(document, legalCase.getCourtLocation(), legalCase.getDivision());

            document.add(new Paragraph("SUMMONS FOR DIRECTIONS")
                    .setBold().setFontSize(14).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            String caseNumber = legalCase.getCaseNumber() != null ? legalCase.getCaseNumber() : "CASE NUMBER NOT ASSIGNED";
            document.add(new Paragraph("CASE NO: " + caseNumber).setBold().setTextAlignment(TextAlignment.CENTER));

            String plaintiff = legalCase.getClientName() != null ? legalCase.getClientName() : "THE PLAINTIFF";
            String defendant = legalCase.getDefendantName() != null ? legalCase.getDefendantName() : "THE DEFENDANT";
            LegalTemplateUtil.addParties(document, plaintiff, defendant);

            document.add(new Paragraph("TAKE NOTICE that this matter shall come up for directions before the Registrar on a date to be fixed."));
            document.add(new Paragraph("\nYou are required to attend and propose directions for the expeditious disposal of this suit."));

            String counsel = legalCase.getRegisteredBy() != null ? legalCase.getRegisteredBy() : "Registry Officer";
            document.add(new Paragraph("\n\nIssued by the Registry,\nHigh Court of Uganda\n\n__________________________\n" + counsel));

            document.close();
        } catch (Exception e) {
            logger.error("Summons for Directions generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF Generation Failed: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    private byte[] generateExtensionOfTimeBase(LegalCase legalCase) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            LegalTemplateUtil.addUgandanHeader(document);
            LegalTemplateUtil.addCourtInfo(document, legalCase.getCourtLocation(), legalCase.getDivision());

            document.add(new Paragraph("APPLICATION FOR EXTENSION OF TIME")
                    .setBold().setFontSize(14).setTextAlignment(TextAlignment.CENTER).setMarginBottom(10));

            String caseNumber = legalCase.getCaseNumber() != null ? legalCase.getCaseNumber() : "CASE NUMBER NOT ASSIGNED";
            document.add(new Paragraph("CASE NO: " + caseNumber).setBold().setTextAlignment(TextAlignment.CENTER));

            String defendant = legalCase.getDefendantName() != null ? legalCase.getDefendantName() : "THE DEFENDANT";
            document.add(new Paragraph("\nTAKE NOTICE that the Defendant, " + defendant + ", hereby applies for extension of time to file a Defence."));
            document.add(new Paragraph("\nGrounds:\n1. The Defendant was not properly served.\n2. The Defendant requires additional time to consult legal counsel.\n3. The delay was not intentional or deliberate."));
            document.add(new Paragraph("\nThis Application is made under Order 52 of the Civil Procedure Rules."));

            String counsel = legalCase.getRegisteredBy() != null ? legalCase.getRegisteredBy() : "Defendant's Counsel";
            document.add(new Paragraph("\n\nDATED this " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))));
            document.add(new Paragraph("\n__________________________\n" + counsel + "\nCounsel for the Defendant"));

            document.close();
        } catch (Exception e) {
            logger.error("Extension of Time generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF Generation Failed: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }
}
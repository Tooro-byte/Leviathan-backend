package com.leviathanledger.leviathan.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.leviathanledger.leviathan.model.LegalCase;
import com.leviathanledger.leviathan.util.LegalTemplateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class PdfGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGeneratorService.class);

    /**
     * Generate Notice of Intention to Sue
     */
    public byte[] generateNoticeOfIntention(LegalCase legalCase) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // 1. Header (The Republic of Uganda)
            LegalTemplateUtil.addUgandanHeader(document);

            // 2. Date
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
            document.add(new Paragraph(date).setTextAlignment(TextAlignment.RIGHT));

            // 3. Recipient (The Defendant)
            String defendantName = legalCase.getDefendantName() != null ? legalCase.getDefendantName().toUpperCase() : "THE DEFENDANT";
            String defendantAddress = legalCase.getDefendantAddress() != null ? legalCase.getDefendantAddress() : "Address Not Provided";

            document.add(new Paragraph("TO: " + defendantName));
            document.add(new Paragraph(defendantAddress));
            document.add(new Paragraph("\n"));

            // 4. Subject Line
            String subject = legalCase.getCaseSubject() != null ? legalCase.getCaseSubject().toUpperCase() : "LEGAL CLAIM";
            document.add(new Paragraph("RE: NOTICE OF INTENTION TO SUE REGARDING " + subject)
                    .setBold()
                    .setUnderline()
                    .setTextAlignment(TextAlignment.CENTER));

            // 5. Body
            String clientName = legalCase.getClientName() != null ? legalCase.getClientName() : "OUR CLIENT";
            String courtLocation = legalCase.getCourtLocation() != null ? legalCase.getCourtLocation() : "KAMPALA";

            document.add(new Paragraph("\nWe act for and on behalf of our client, " + clientName + ", who has instructed us to address you as follows:"));

            document.add(new Paragraph("\nTAKE NOTICE that unless you satisfy our client's claim within seven (7) days from the date of receipt of this notice, we have firm instructions to institute legal proceedings against you in the High Court of Uganda at " + courtLocation + "."));

            document.add(new Paragraph("\nThis will be at your own peril as to costs and other incidental consequences."));

            document.add(new Paragraph("\nSTAND PROPERLY ADVISED."));

            // 6. Sign-off
            String counsel = legalCase.getRegisteredBy() != null ? legalCase.getRegisteredBy() : "Legal Counsel";
            document.add(new Paragraph("\n\n__________________________"));
            document.add(new Paragraph("Counsel for the Plaintiff (" + counsel + ")"));

            // 7. Digital Signature
            LegalTemplateUtil.addDigitalSignature(document, legalCase.getCaseNumber());

            document.close();
            logger.info("Successfully generated Notice of Intention for case: {}", legalCase.getCaseNumber());

        } catch (Exception e) {
            logger.error("PDF Generation Failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF Generation Failed: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    /**
     * Generate Summons to File Defence
     */
    public byte[] generateSummonsToFileDefence(LegalCase legalCase) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // 1. Header
            LegalTemplateUtil.addUgandanHeader(document);
            LegalTemplateUtil.addCourtInfo(document, legalCase.getCourtLocation(), legalCase.getDivision());

            // 2. Title
            document.add(new Paragraph("SUMMONS TO FILE DEFENCE")
                    .setBold()
                    .setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            // 3. Case Details
            String caseNumber = legalCase.getCaseNumber() != null ? legalCase.getCaseNumber() : "CASE NUMBER NOT ASSIGNED";
            document.add(new Paragraph("CASE NO: " + caseNumber)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            // 4. Parties
            String plaintiff = legalCase.getClientName() != null ? legalCase.getClientName() : "THE PLAINTIFF";
            String defendant = legalCase.getDefendantName() != null ? legalCase.getDefendantName() : "THE DEFENDANT";
            LegalTemplateUtil.addParties(document, plaintiff, defendant);

            // 5. Body
            document.add(new Paragraph("TO: " + defendant.toUpperCase()));
            document.add(new Paragraph("\nTAKE NOTICE that the Plaintiff has instituted a suit against you in this Honourable Court."));
            document.add(new Paragraph("\nYou are hereby required to file a Defence within fourteen (14) days from the date of service of this Summons."));
            document.add(new Paragraph("\nIf you fail to file a Defence within the stipulated time, the Plaintiff may proceed to judgment against you without further notice."));
            document.add(new Paragraph("\nTAKE FURTHER NOTICE that this matter shall be mentioned before the Registrar on a date to be fixed for directions."));

            // 6. Sign-off
            String counsel = legalCase.getRegisteredBy() != null ? legalCase.getRegisteredBy() : "Registry Officer";
            document.add(new Paragraph("\n\nIssued by the Registry,"));
            document.add(new Paragraph("High Court of Uganda at " + (legalCase.getCourtLocation() != null ? legalCase.getCourtLocation() : "Kampala")));
            document.add(new Paragraph("\n\n__________________________"));
            document.add(new Paragraph(counsel));

            LegalTemplateUtil.addDigitalSignature(document, legalCase.getCaseNumber());
            document.close();

        } catch (Exception e) {
            logger.error("Summons Generation Failed: {}", e.getMessage(), e);
            throw new RuntimeException("Summons Generation Failed: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    /**
     * Generate Originating Summons
     */
    public byte[] generateOriginatingSummons(LegalCase legalCase) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            LegalTemplateUtil.addUgandanHeader(document);
            LegalTemplateUtil.addCourtInfo(document, legalCase.getCourtLocation(), legalCase.getDivision());

            document.add(new Paragraph("ORIGINATING SUMMONS")
                    .setBold()
                    .setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            String caseNumber = legalCase.getCaseNumber() != null ? legalCase.getCaseNumber() : "CASE NUMBER NOT ASSIGNED";
            document.add(new Paragraph("CASE NO: " + caseNumber)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            String plaintiff = legalCase.getClientName() != null ? legalCase.getClientName() : "THE PLAINTIFF";
            String defendant = legalCase.getDefendantName() != null ? legalCase.getDefendantName() : "THE DEFENDANT";
            LegalTemplateUtil.addParties(document, plaintiff, defendant);

            document.add(new Paragraph("LET the Defendant appear before this Honourable Court within fifteen (15) days from the date of service to show cause why the orders sought should not be granted."));
            document.add(new Paragraph("\nTAKE NOTICE that this Summons shall be heard on a date to be fixed by the Registrar."));

            String counsel = legalCase.getRegisteredBy() != null ? legalCase.getRegisteredBy() : "Registry Officer";
            document.add(new Paragraph("\n\nIssued by the Registry,\nHigh Court of Uganda\n\n__________________________\n" + counsel));

            LegalTemplateUtil.addDigitalSignature(document, legalCase.getCaseNumber());
            document.close();

        } catch (Exception e) {
            logger.error("Originating Summons Generation Failed: {}", e.getMessage(), e);
            throw new RuntimeException("Originating Summons Generation Failed: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    /**
     * Generate Summons for Directions
     */
    public byte[] generateSummonsForDirections(LegalCase legalCase) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            LegalTemplateUtil.addUgandanHeader(document);
            LegalTemplateUtil.addCourtInfo(document, legalCase.getCourtLocation(), legalCase.getDivision());

            document.add(new Paragraph("SUMMONS FOR DIRECTIONS")
                    .setBold()
                    .setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            String caseNumber = legalCase.getCaseNumber() != null ? legalCase.getCaseNumber() : "CASE NUMBER NOT ASSIGNED";
            document.add(new Paragraph("CASE NO: " + caseNumber)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            String plaintiff = legalCase.getClientName() != null ? legalCase.getClientName() : "THE PLAINTIFF";
            String defendant = legalCase.getDefendantName() != null ? legalCase.getDefendantName() : "THE DEFENDANT";
            LegalTemplateUtil.addParties(document, plaintiff, defendant);

            document.add(new Paragraph("TAKE NOTICE that this matter shall come up for directions before the Registrar on a date to be fixed."));
            document.add(new Paragraph("\nYou are required to attend and propose directions for the expeditious disposal of this suit."));

            String counsel = legalCase.getRegisteredBy() != null ? legalCase.getRegisteredBy() : "Registry Officer";
            document.add(new Paragraph("\n\nIssued by the Registry,\nHigh Court of Uganda\n\n__________________________\n" + counsel));

            LegalTemplateUtil.addDigitalSignature(document, legalCase.getCaseNumber());
            document.close();

        } catch (Exception e) {
            logger.error("Summons for Directions Generation Failed: {}", e.getMessage(), e);
            throw new RuntimeException("Summons for Directions Generation Failed: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    /**
     * Generate Application for Extension of Time
     */
    public byte[] generateExtensionOfTime(LegalCase legalCase) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            LegalTemplateUtil.addUgandanHeader(document);
            LegalTemplateUtil.addCourtInfo(document, legalCase.getCourtLocation(), legalCase.getDivision());

            document.add(new Paragraph("APPLICATION FOR EXTENSION OF TIME")
                    .setBold()
                    .setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            String caseNumber = legalCase.getCaseNumber() != null ? legalCase.getCaseNumber() : "CASE NUMBER NOT ASSIGNED";
            document.add(new Paragraph("CASE NO: " + caseNumber)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER));

            String defendant = legalCase.getDefendantName() != null ? legalCase.getDefendantName() : "THE DEFENDANT";
            document.add(new Paragraph("\nTAKE NOTICE that the Defendant, " + defendant + ", hereby applies for extension of time to file a Defence."));
            document.add(new Paragraph("\nGrounds:\n1. The Defendant was not properly served.\n2. The Defendant requires additional time to consult legal counsel.\n3. The delay was not intentional or deliberate."));
            document.add(new Paragraph("\nThis Application is made under Order 52 of the Civil Procedure Rules."));

            String counsel = legalCase.getRegisteredBy() != null ? legalCase.getRegisteredBy() : "Defendant's Counsel";
            document.add(new Paragraph("\n\nDATED this " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))));
            document.add(new Paragraph("\n__________________________\n" + counsel + "\nCounsel for the Defendant"));

            LegalTemplateUtil.addDigitalSignature(document, legalCase.getCaseNumber());
            document.close();

        } catch (Exception e) {
            logger.error("Extension of Time Generation Failed: {}", e.getMessage(), e);
            throw new RuntimeException("Extension of Time Generation Failed: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }
}
package com.leviathanledger.leviathan.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Hashtable;

@Service
public class QRCodeService {

    /**
     * Generate QR code as BufferedImage
     */
    public BufferedImage generateQRCode(String content, int width, int height) throws WriterException {
        Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, bitMatrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return image;
    }

    /**
     * Generate QR code as byte array (PNG format)
     */
    public byte[] generateQRCodeBytes(String content, int width, int height) throws WriterException {
        BufferedImage qrImage = generateQRCode(content, width, height);

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try {
            javax.imageio.ImageIO.write(qrImage, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code image", e);
        }
    }
}
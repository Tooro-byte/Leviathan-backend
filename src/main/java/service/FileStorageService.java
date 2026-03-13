package com.leviathanledger.leviathan.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class FileStorageService {

    private final Path root = Paths.get("uploads");

    public String saveToDisk(MultipartFile file) throws Exception {
        if (!Files.exists(root)) Files.createDirectories(root);

        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Files.copy(file.getInputStream(), this.root.resolve(filename));

        return filename;
    }

    public String calculateHash(MultipartFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(file.getBytes());
        return HexFormat.of().formatHex(hash);
    }
}
package com.scan.api.service;

import com.scan.api.domain.Document;

import java.util.UUID;

public interface DocumentService {

    Document savePdf(String originalFilename, String contentType,
                     java.io.InputStream in, long size) throws java.io.IOException;

    DownloadResult download(UUID id);

    record DownloadResult(String filename, String contentType, byte[] bytes) {}
}

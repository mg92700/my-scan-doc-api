package com.scan.api.storage;

import java.io.InputStream;

public interface StorageService {
    String store(InputStream input, long size, String contentType, String suggestedExt);
    byte[] get(String key);
}

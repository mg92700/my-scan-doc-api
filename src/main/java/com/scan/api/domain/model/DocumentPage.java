package com.scan.api.domain.model;

import java.util.List;

/**
 * Une page de résultats : les documents demandés et le nombre total de correspondances
 * (pour que le client sache combien de pages existent).
 */
public record DocumentPage(List<Document> items, long total) {}

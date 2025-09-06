package com.example.mdt.domain.model;

import java.time.LocalDateTime;

public record Deteccion(Long lectorId, Long ubicacionId, String epc, Integer rssi, String machine,
                        LocalDateTime createdAt, String version) {
}

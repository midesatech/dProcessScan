package com.example.mdt.domain.model;

import java.util.List;

public record Scan(String datatype, String stage, String device, String machine, List<String> csn) {
}

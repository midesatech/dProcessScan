package com.example.mdt.domain.gateway;

import java.util.Optional;

public interface MetadataGateway {
    Optional<Long> findLectorIdByCodigo(String codigo);
    boolean existsUbicacionId(Long id);
}

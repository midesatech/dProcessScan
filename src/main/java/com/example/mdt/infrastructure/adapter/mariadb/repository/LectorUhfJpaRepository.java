package com.example.mdt.infrastructure.adapter.mariadb.repository;

import com.example.mdt.infrastructure.adapter.mariadb.entity.UHFReaderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LectorUhfJpaRepository extends JpaRepository<UHFReaderEntity, Long> {
    Optional<UHFReaderEntity> findByCodigo(String codigo);
}

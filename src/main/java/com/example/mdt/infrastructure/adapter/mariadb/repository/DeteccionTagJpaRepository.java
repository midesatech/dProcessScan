package com.example.mdt.infrastructure.adapter.mariadb.repository;

import com.example.mdt.infrastructure.adapter.mariadb.entity.DeteccionTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeteccionTagJpaRepository extends JpaRepository<DeteccionTagEntity, Long> {
}

package com.example.gbt28181.domain.repository;

import com.example.gbt28181.domain.entity.LocalSipConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LocalSipConfigRepository extends JpaRepository<LocalSipConfig, Long> {
}

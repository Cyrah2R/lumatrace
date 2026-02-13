package org.lumatrace.cloud.repository;

import org.lumatrace.cloud.model.PhotoRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PhotoRepository extends JpaRepository<PhotoRegistration, UUID> {
}

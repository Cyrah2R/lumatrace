package org.lumatrace.cloud.repository;

import org.lumatrace.cloud.model.PhotoRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PhotoRepository extends JpaRepository<PhotoRegistration, UUID> {
    // Solo con esto, Spring ya sabe hacer: save(), findById(), delete(), etc.
}
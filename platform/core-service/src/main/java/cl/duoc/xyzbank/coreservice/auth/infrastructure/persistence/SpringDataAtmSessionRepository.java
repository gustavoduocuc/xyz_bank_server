package cl.duoc.xyzbank.coreservice.auth.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataAtmSessionRepository extends JpaRepository<AtmSessionJpaEntity, UUID> {
}

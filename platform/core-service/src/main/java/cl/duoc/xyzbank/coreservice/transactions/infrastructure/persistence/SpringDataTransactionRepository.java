package cl.duoc.xyzbank.coreservice.transactions.infrastructure.persistence;

import cl.duoc.xyzbank.coredomain.transactions.domain.valueobjects.TransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface SpringDataTransactionRepository extends JpaRepository<TransactionJpaEntity, UUID> {

    @Query("""
            SELECT t FROM TransactionJpaEntity t
            WHERE t.accountId = :accountId
              AND (:from IS NULL OR t.occurredOn >= :from)
              AND (:to IS NULL OR t.occurredOn <= :to)
              AND (:type IS NULL OR t.type = :type)
              AND (
                    :cursorOccurredOn IS NULL
                    OR t.occurredOn < :cursorOccurredOn
                    OR (t.occurredOn = :cursorOccurredOn AND t.id < :cursorId)
                  )
            ORDER BY t.occurredOn DESC, t.id DESC
            """)
    List<TransactionJpaEntity> findPage(
            @Param("accountId") UUID accountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("type") TransactionType type,
            @Param("cursorOccurredOn") LocalDate cursorOccurredOn,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}

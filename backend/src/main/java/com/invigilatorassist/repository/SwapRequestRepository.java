package com.invigilatorassist.repository;

import com.invigilatorassist.domain.SwapRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SwapRequestRepository extends JpaRepository<SwapRequest, Long> {

    @Query("""
            select s from SwapRequest s
            join fetch s.requesterDuty rd join fetch rd.roomAllocation rra join fetch rra.room
            join fetch rd.faculty rf join fetch rf.user
            join fetch s.targetDuty td join fetch td.roomAllocation tra join fetch tra.room
            join fetch td.faculty tf join fetch tf.user
            where rf.id = :facultyId or tf.id = :facultyId
            order by s.createdAt desc""")
    List<SwapRequest> findAllInvolvingFaculty(@Param("facultyId") Long facultyId);

    @Query("""
            select count(s) > 0 from SwapRequest s
            where s.status = :status
              and s.requesterDuty.id = :requesterDutyId and s.targetDuty.id = :targetDutyId""")
    boolean existsWithStatus(@Param("requesterDutyId") Long requesterDutyId, @Param("targetDutyId") Long targetDutyId,
                             @Param("status") SwapRequest.Status status);

    default boolean existsPending(Long requesterDutyId, Long targetDutyId) {
        return existsWithStatus(requesterDutyId, targetDutyId, SwapRequest.Status.PENDING);
    }

    /** Other open requests that touch either duty; they become stale once a swap is accepted. */
    @Query("""
            select s from SwapRequest s
            where s.status = :status and s.id <> :exceptId
              and (s.requesterDuty.id in :dutyIds or s.targetDuty.id in :dutyIds)""")
    List<SwapRequest> findTouchingWithStatus(@Param("dutyIds") List<Long> dutyIds, @Param("exceptId") Long exceptId,
                                             @Param("status") SwapRequest.Status status);

    default List<SwapRequest> findPendingTouching(List<Long> dutyIds, Long exceptId) {
        return findTouchingWithStatus(dutyIds, exceptId, SwapRequest.Status.PENDING);
    }
}

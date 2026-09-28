package com.example.esgaward.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.esgaward.entity.Proposal;
import com.example.esgaward.entity.ProposalMember;

public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    @Query("""
            select p from Proposal p
            where (:awardEventId is null or p.awardEvent.id = :awardEventId)
            order by p.createdAt desc
            """)
    List<Proposal> findAllByEvent(@Param("awardEventId") Long awardEventId);

    /** The given proposals (e.g. the ones OpenFGA says a user may view), optionally within one award event. */
    @Query("""
            select p from Proposal p
            where p.id in :ids
              and (:awardEventId is null or p.awardEvent.id = :awardEventId)
            order by p.createdAt desc
            """)
    List<Proposal> findAllByIdInAndEvent(@Param("ids") Collection<Long> ids, @Param("awardEventId") Long awardEventId);

    boolean existsByAwardEventId(Long awardEventId);
}

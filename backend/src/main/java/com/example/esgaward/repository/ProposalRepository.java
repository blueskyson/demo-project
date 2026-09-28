package com.example.esgaward.repository;

import java.util.List;
import java.util.UUID;

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

    /** Proposals the user leads or is a member of. */
    @Query("""
            select p from Proposal p
            where (:awardEventId is null or p.awardEvent.id = :awardEventId)
              and (p.leader.id = :userId
                   or exists (select 1 from ProposalMember m where m.proposal = p and m.user.id = :userId))
            order by p.createdAt desc
            """)
    List<Proposal> findAllVisibleTo(@Param("userId") UUID userId, @Param("awardEventId") Long awardEventId);

    boolean existsByAwardEventId(Long awardEventId);
}

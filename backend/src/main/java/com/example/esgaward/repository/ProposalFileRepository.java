package com.example.esgaward.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.esgaward.entity.ProposalFile;

public interface ProposalFileRepository extends JpaRepository<ProposalFile, Long> {
}

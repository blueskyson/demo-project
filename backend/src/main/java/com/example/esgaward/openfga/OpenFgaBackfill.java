package com.example.esgaward.openfga;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.esgaward.entity.AwardEvent;
import com.example.esgaward.repository.AwardEventRepository;
import com.example.esgaward.repository.ProposalRepository;

import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;

/**
 * On startup, writes the tuples for every award event and proposal in the database. Idempotent,
 * so it's safe to run every time; it's what populates a fresh OpenFGA store for existing data and
 * repairs tuples that went missing. It does not remove stale tuples.
 */
@Component
@ConditionalOnProperty(name = "openfga.backfill-on-startup", havingValue = "true")
public class OpenFgaBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenFgaBackfill.class);

    private final AwardEventRepository awardEventRepository;
    private final ProposalRepository proposalRepository;
    private final RelationshipTuples relationshipTuples;
    private final OpenFgaGateway gateway;

    public OpenFgaBackfill(AwardEventRepository awardEventRepository, ProposalRepository proposalRepository,
            RelationshipTuples relationshipTuples, OpenFgaGateway gateway) {
        this.awardEventRepository = awardEventRepository;
        this.proposalRepository = proposalRepository;
        this.relationshipTuples = relationshipTuples;
        this.gateway = gateway;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        List<AwardEvent> events = awardEventRepository.findAll();
        List<ClientTupleKey> tuples = new ArrayList<>();
        events.forEach(e -> tuples.addAll(relationshipTuples.awardEventTuples(e)));
        proposalRepository.findAll().forEach(p -> tuples.addAll(relationshipTuples.proposalTuples(p)));

        // "open" tuples carry the deadline as condition context; an existing tuple with a different
        // deadline would conflict, so re-create them instead of relying on duplicate-ignoring.
        List<ClientTupleKeyWithoutCondition> openKeys = events.stream()
                .map(e -> RelationshipTuples.key(Fga.ALL_USERS, Fga.OPEN, Fga.awardEvent(e.getId())))
                .toList();
        gateway.delete(openKeys);
        gateway.write(tuples);
        log.info("OpenFGA backfill wrote {} tuples", tuples.size());
    }
}

package com.example.esgaward.openfga;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.esgaward.entity.AwardEvent;
import com.example.esgaward.entity.Proposal;

import dev.openfga.sdk.api.client.model.ClientRelationshipCondition;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;

/**
 * Keeps OpenFGA's relationship tuples in sync with the database. Services call these right after
 * changing the corresponding rows, inside their transaction: if the OpenFGA write fails, the
 * exception rolls the database change back too.
 *
 * <p>This is a simple dual write. If the database commit fails <em>after</em> the OpenFGA write,
 * the two can drift; {@link OpenFgaBackfill} repairs missing tuples on the next startup. A
 * production system would use a transactional outbox instead.
 */
@Component
public class RelationshipTuples {

    private final OpenFgaGateway gateway;

    public RelationshipTuples(OpenFgaGateway gateway) {
        this.gateway = gateway;
    }

    public void awardEventCreated(AwardEvent event) {
        gateway.write(awardEventTuples(event));
    }

    /**
     * The deadline lives in the {@code open} tuple's condition context; a tuple's condition can't be
     * updated in place, so delete and re-create it.
     */
    public void awardEventDeadlineChanged(AwardEvent event) {
        gateway.delete(List.of(key(Fga.ALL_USERS, Fga.OPEN, Fga.awardEvent(event.getId()))));
        gateway.write(List.of(openTuple(event)));
    }

    public void awardEventDeleted(Long awardEventId) {
        String object = Fga.awardEvent(awardEventId);
        gateway.delete(List.of(
                key(Fga.SYSTEM, Fga.SYSTEM_RELATION, object),
                key(Fga.ALL_USERS, Fga.VIEWER, object),
                key(Fga.ALL_USERS, Fga.OPEN, object)));
    }

    public void proposalCreated(Proposal proposal) {
        gateway.write(proposalTuples(proposal));
    }

    public void leaderChanged(Long proposalId, UUID oldLeaderId, UUID newLeaderId) {
        gateway.delete(List.of(key(Fga.user(oldLeaderId), Fga.LEADER, Fga.proposal(proposalId))));
        gateway.write(List.of(tuple(Fga.user(newLeaderId), Fga.LEADER, Fga.proposal(proposalId))));
    }

    public void memberAdded(Long proposalId, UUID userId) {
        gateway.write(List.of(tuple(Fga.user(userId), Fga.MEMBER, Fga.proposal(proposalId))));
    }

    public void memberRemoved(Long proposalId, UUID userId) {
        gateway.delete(List.of(key(Fga.user(userId), Fga.MEMBER, Fga.proposal(proposalId))));
    }

    public void proposalDeleted(Proposal proposal) {
        gateway.delete(proposalTuples(proposal).stream()
                .map(t -> key(t.getUser(), t.getRelation(), t.getObject()))
                .toList());
    }

    /** All tuples describing an award event. */
    List<ClientTupleKey> awardEventTuples(AwardEvent event) {
        String object = Fga.awardEvent(event.getId());
        return List.of(
                tuple(Fga.SYSTEM, Fga.SYSTEM_RELATION, object),
                tuple(Fga.ALL_USERS, Fga.VIEWER, object),
                openTuple(event));
    }

    /** All tuples describing a proposal: its award event, leader and members. */
    List<ClientTupleKey> proposalTuples(Proposal proposal) {
        String object = Fga.proposal(proposal.getId());
        List<ClientTupleKey> tuples = new ArrayList<>();
        tuples.add(tuple(Fga.awardEvent(proposal.getAwardEvent().getId()), Fga.AWARD_EVENT_RELATION, object));
        tuples.add(tuple(Fga.user(proposal.getLeader().getId()), Fga.LEADER, object));
        proposal.getMembers().forEach(m -> tuples.add(tuple(Fga.user(m.getUser().getId()), Fga.MEMBER, object)));
        return tuples;
    }

    ClientTupleKey openTuple(AwardEvent event) {
        return tuple(Fga.ALL_USERS, Fga.OPEN, Fga.awardEvent(event.getId()))
                .condition(new ClientRelationshipCondition()
                        .name(Fga.BEFORE_DEADLINE)
                        .context(Map.of(Fga.DEADLINE_PARAM, event.getDeadline().toString())));
    }

    static ClientTupleKey tuple(String user, String relation, String object) {
        return new ClientTupleKey().user(user).relation(relation)._object(object);
    }

    static ClientTupleKeyWithoutCondition key(String user, String relation, String object) {
        return new ClientTupleKeyWithoutCondition().user(user).relation(relation)._object(object);
    }
}

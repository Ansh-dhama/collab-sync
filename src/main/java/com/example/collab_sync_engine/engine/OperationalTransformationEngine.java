package com.example.collab_sync_engine.engine;

import com.example.collab_sync_engine.Enum.OperationType;
import com.example.collab_sync_engine.dto.OperationRequest;
import com.example.collab_sync_engine.model.DocumentOperation;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OperationalTransformationEngine {

    public OperationRequest transformAgainstHistory(
            OperationRequest incoming,
            List<DocumentOperation> operationsAfterBaseVersion
    ) {
        OperationRequest transformed = copy(incoming);

        for (DocumentOperation appliedOperation : operationsAfterBaseVersion) {
            transformed = transform(transformed, appliedOperation);

            if (transformed.getType() == OperationType.NO_OP) {
                return transformed;
            }
        }

        return transformed;
    }

    private OperationRequest transform(
            OperationRequest incoming,
            DocumentOperation appliedOperation
    ) {
        if (incoming.getType() == OperationType.NO_OP) {
            return incoming;
        }

        if (appliedOperation.getType() == OperationType.NO_OP) {
            return incoming;
        }

        if (appliedOperation.getType() == OperationType.INSERT) {
            return transformAgainstInsert(incoming, appliedOperation);
        }

        if (appliedOperation.getType() == OperationType.DELETE) {
            return transformAgainstDelete(incoming, appliedOperation);
        }

        return incoming;
    }

    private OperationRequest transformAgainstInsert(
            OperationRequest incoming,
            DocumentOperation appliedInsert
    ) {
        int insertedLength = appliedInsert.getText() == null
                ? 0
                : appliedInsert.getText().length();

        if (insertedLength == 0) {
            return incoming;
        }

        int appliedPosition = appliedInsert.getPosition();

        if (incoming.getType() == OperationType.INSERT) {
            /*
             * Already applied insert happened before or at incoming insert position.
             * So incoming insert position shifts right.
             */
            if (appliedPosition <= incoming.getPosition()) {
                incoming.setPosition(incoming.getPosition() + insertedLength);
            }

            return incoming;
        }

        if (incoming.getType() == OperationType.DELETE) {
            int deleteStart = incoming.getPosition();
            int deleteEnd = incoming.getPosition() + incoming.getLength();

            /*
             * Insert happened before delete range.
             * Shift delete position right.
             */
            if (appliedPosition <= deleteStart) {
                incoming.setPosition(incoming.getPosition() + insertedLength);
                return incoming;
            }

            /*
             * Insert happened inside delete range.
             * For this simple OT version, delete range expands to cover inserted text too.
             */
            if (appliedPosition > deleteStart && appliedPosition < deleteEnd) {
                incoming.setLength(incoming.getLength() + insertedLength);
                return incoming;
            }

            /*
             * Insert happened after delete range.
             * No change.
             */
            return incoming;
        }

        return incoming;
    }

    private OperationRequest transformAgainstDelete(
            OperationRequest incoming,
            DocumentOperation appliedDelete
    ) {
        int appliedStart = appliedDelete.getPosition();
        int appliedLength = appliedDelete.getLength() == null
                ? 0
                : appliedDelete.getLength();
        int appliedEnd = appliedStart + appliedLength;

        if (appliedLength <= 0) {
            return incoming;
        }

        if (incoming.getType() == OperationType.INSERT) {
            return transformInsertAgainstDelete(incoming, appliedStart, appliedEnd);
        }

        if (incoming.getType() == OperationType.DELETE) {
            return transformDeleteAgainstDelete(incoming, appliedStart, appliedEnd);
        }

        return incoming;
    }

    private OperationRequest transformInsertAgainstDelete(
            OperationRequest incoming,
            int appliedStart,
            int appliedEnd
    ) {
        int incomingPosition = incoming.getPosition();
        int appliedLength = appliedEnd - appliedStart;

        /*
         * Delete happened completely before incoming insert position.
         * Shift insert position left.
         */
        if (appliedEnd <= incomingPosition) {
            incoming.setPosition(incomingPosition - appliedLength);
            return incoming;
        }

        /*
         * Incoming insert position is inside deleted range.
         * Move insert to start of deleted range.
         */
        if (appliedStart < incomingPosition && incomingPosition < appliedEnd) {
            incoming.setPosition(appliedStart);
            return incoming;
        }

        /*
         * Delete happened after incoming insert position.
         * No change.
         */
        return incoming;
    }

    private OperationRequest transformDeleteAgainstDelete(
            OperationRequest incoming,
            int appliedStart,
            int appliedEnd
    ) {
        int incomingStart = incoming.getPosition();
        int incomingLength = incoming.getLength() == null
                ? 0
                : incoming.getLength();
        int incomingEnd = incomingStart + incomingLength;

        if (incomingLength <= 0) {
            incoming.setType(OperationType.NO_OP);
            incoming.setLength(0);
            return incoming;
        }

        int appliedLength = appliedEnd - appliedStart;

        /*
         * Applied delete happened completely before incoming delete.
         * Shift incoming delete left.
         */
        if (appliedEnd <= incomingStart) {
            incoming.setPosition(incomingStart - appliedLength);
            return incoming;
        }

        /*
         * Applied delete happened completely after incoming delete.
         * No change.
         */
        if (appliedStart >= incomingEnd) {
            return incoming;
        }

        /*
         * Overlapping delete case.
         */
        int overlapStart = Math.max(incomingStart, appliedStart);
        int overlapEnd = Math.min(incomingEnd, appliedEnd);
        int overlapLength = Math.max(0, overlapEnd - overlapStart);

        int newLength = incomingLength - overlapLength;

        if (newLength <= 0) {
            incoming.setType(OperationType.NO_OP);
            incoming.setLength(0);
            incoming.setPosition(Math.min(incomingStart, appliedStart));
            return incoming;
        }

        /*
         * If applied delete started before incoming delete,
         * incoming position shifts to appliedStart.
         */
        if (appliedStart < incomingStart) {
            incoming.setPosition(appliedStart);
        }

        incoming.setLength(newLength);
        return incoming;
    }

    private OperationRequest copy(OperationRequest request) {
        return new OperationRequest(
                request.getOperationId(),
                request.getClientId(),
                request.getBaseVersion(),
                request.getType(),
                request.getPosition(),
                request.getText(),
                request.getLength()
        );
    }
}
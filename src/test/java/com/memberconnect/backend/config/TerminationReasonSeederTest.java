package com.memberconnect.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memberconnect.backend.model.TerminationReason;
import com.memberconnect.backend.repository.TerminationReasonRepository;

/**
 * Unit tests for the Termination Reasons Master seeder. No Spring context and no
 * database - the repository is mocked, so running these never writes anywhere.
 */
@ExtendWith(MockitoExtension.class)
class TerminationReasonSeederTest {

    @Mock
    private TerminationReasonRepository terminationReasonRepository;

    @InjectMocks
    private TerminationReasonSeeder seeder;

    @Test
    void seedsTheFourSrsReasonsIntoAnEmptyMaster() {
        when(terminationReasonRepository.count()).thenReturn(0L);

        seeder.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TerminationReason>> captor = ArgumentCaptor.forClass(List.class);
        verify(terminationReasonRepository).saveAll(captor.capture());

        List<TerminationReason> seeded = captor.getValue();

        assertThat(seeded).extracting(TerminationReason::getCode)
                .containsExactly("RESIGNATION", "DISCIPLINARY", "TRANSFER", "OTHER");
        assertThat(seeded).extracting(TerminationReason::getName)
                .containsExactly(
                        "Resignation from Post",
                        "Disciplinary Action",
                        "Transfer to Another Organization",
                        "Other"
                );
        assertThat(seeded).extracting(TerminationReason::getDisplayOrder)
                .containsExactly(1, 2, 3, 4);
        assertThat(seeded).allMatch(TerminationReason::isActive);
    }

    @Test
    void doesNothingWhenTheMasterAlreadyHasRows() {
        when(terminationReasonRepository.count()).thenReturn(4L);

        seeder.run();

        // No insert, no update, no delete - existing master rows are never touched.
        verify(terminationReasonRepository, never()).saveAll(any());
        verify(terminationReasonRepository, never()).save(any());
        verify(terminationReasonRepository, never()).deleteAll();
    }

    @Test
    void isIdempotentAcrossRestarts() {
        when(terminationReasonRepository.count()).thenReturn(0L, 4L);

        seeder.run();
        seeder.run();

        // Seeded exactly once, however many times the application starts.
        verify(terminationReasonRepository).saveAll(any());
    }
}

package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.BookingStore;
import edu.cmu.cs214.scheduling.domain.Member;
import edu.cmu.cs214.scheduling.domain.MembershipTier;
import edu.cmu.cs214.scheduling.domain.Room;
import edu.cmu.cs214.scheduling.domain.TimeSlot;
import edu.cmu.cs214.scheduling.notify.NotificationHub;
import edu.cmu.cs214.scheduling.pricing.PriceCalculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization pins for {@link BookingWorkflow}: they record what the shipped code does,
 * not what it should do.
 */
class BookingWorkflowCharacterizationTest {

    private static final LocalDateTime MON_8AM = LocalDateTime.of(2026, 10, 5, 8, 0);
    private static final LocalDateTime MON_9AM = LocalDateTime.of(2026, 10, 5, 9, 0);
    private static final LocalDateTime MON_10AM = LocalDateTime.of(2026, 10, 5, 10, 0);

    private BookingWorkflow workflow;

    @BeforeEach
    void setUp() {
        BookingStore store = new BookingStore();
        store.addRoom(new Room("W-101", "Willow Room", 8));
        store.addRoom(new Room("C-200", "Cedar Hall", 20));
        store.addMember(new Member("m-1", "Ada", "ada@rooms.example.edu", MembershipTier.BASIC));
        store.addMember(new Member("m-2", "Grace", "grace@rooms.example.edu",
                MembershipTier.PREMIER));
        workflow = new BookingWorkflow(store, new PriceCalculator(), new NotificationHub());
    }

    /**
     * The recurring overlap check uses {@code <=}, so a week that starts exactly when another
     * booking ends counts as taken and is skipped. A regular booking in the same spot is
     * accepted (see {@code BookingWorkflowTest.regularSubmitAcceptsASlotThatStartsWhenAnotherEnds}).
     */
    @Test
    void recurringSeriesSkipsAWeekThatStartsWhenAnExistingBookingEnds() {
        workflow.submit(BookingRequest.regular("C-200", "m-2",
                MON_8AM.plusWeeks(1), MON_9AM.plusWeeks(1), 4));

        BookingOutcome outcome = workflow.submit(
                BookingRequest.recurring("C-200", "m-1", MON_9AM, MON_10AM, 3, 6));

        assertTrue(outcome.isAccepted());
        assertEquals(List.of(new TimeSlot(MON_9AM.plusWeeks(1), MON_10AM.plusWeeks(1))),
                outcome.getSkipped());
        assertEquals(List.of(1, 3),
                outcome.getBooked().stream().map(Booking::getOccurrenceIndex).toList());
        assertEquals("series S-1: 2 booked, 1 skipped", outcome.getMessage());
    }
}

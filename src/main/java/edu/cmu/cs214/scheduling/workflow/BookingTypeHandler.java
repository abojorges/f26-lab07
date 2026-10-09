package edu.cmu.cs214.scheduling.workflow;

import edu.cmu.cs214.scheduling.domain.Booking;
import edu.cmu.cs214.scheduling.domain.BookingOutcome;
import edu.cmu.cs214.scheduling.domain.BookingRequest;
import edu.cmu.cs214.scheduling.domain.Room;

/**
 * What {@link BookingWorkflow} does that depends on the booking type. The workflow
 * does the shared lookups first and hands each call to the handler for that type.
 */
interface BookingTypeHandler {

    /** Validates and writes a request whose room is already known to exist. */
    BookingOutcome submit(BookingRequest request, Room room);

    /** Releases a live booking. */
    boolean cancel(Booking booking, String roomName, boolean adminOverride);

    /** What the holder owes, in dollars. */
    double priceOf(Booking booking);

    /** A one-line summary for schedules and confirmation screens. */
    String describe(Booking booking, String roomName);
}

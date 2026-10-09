# REFACTOR.md

One section per milestone. Fill each one in as you go, in order.

Milestone 1 is written in two sittings, the pin before the refactor and the
rest after. A pin written afterwards is worth nothing, and a TA will ask.

Keep it short and specific. Point at methods, call sites, and test names.

---

## Milestone 1: Direct a refactor, characterization first

### The pin (write this section before you direct the refactor)

**The pin.**
`src/test/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflowCharacterizationTest.java`,
`recurringSeriesSkipsAWeekThatStartsWhenAnExistingBookingEnds`. It pins this:
`BookingWorkflow.submit` on a 3-week recurring request (C-200, Mondays
9:00–10:00 from Oct 5) skips week 2, which starts exactly when an existing
8:00–9:00 booking ends. The outcome is accepted, with `getSkipped()` equal to
`[2026-10-12T09:00 to 2026-10-12T10:00]`, occurrence indexes `[1, 3]`, and the
message `series S-1: 2 booked, 1 skipped`.

The pin is green against the shipped code (`Tests run: 36, Failures: 0`). It
lives in a new class, so no existing test method was touched. The expected
values come from the code itself: I first asserted `3 booked, 0 skipped`, and
the failure printed the actual result.

**Why that one, and does a shipped test already cover it?**
- **Why it matters.** The recurring room check (`BookingWorkflow.java:121-122`)
  uses `<= 0`. The other three overlap checks (`:68-69`, `:79-80`, `:152-153`)
  use `< 0`, and `TimeSlot` documents an exclusive end. The refactor puts this
  boundary at risk: four near-identical overlap checks invite one shared
  helper, and that helper would quietly turn the recurring `<=` into `<`.
- **No shipped test covers it.**
  - `regularSubmitAcceptsASlotThatStartsWhenAnotherEnds` pins the same
    boundary, but for REGULAR only.
  - `recurringSubmitBooksEveryWeekOfAnOpenSeries` uses an empty room, so the
    skip branch never runs.
  - A grep shows no shipped test reads `getSkipped()`.
- **How I checked.** With `<=` changed to `<` at `:121-122`, the shipped 35
  tests stay green and only this pin fails.
- I am not claiming `<=` is right. Fixing it would be a behavior change in its
  own commit, not part of this refactor.

**What a regeneration would do differently here.** The decision is whether a
weekly occurrence that only touches an existing booking counts as a conflict.
A regeneration would almost certainly write one half-open overlap check
(`start < otherEnd && otherStart < end`), matching `TimeSlot` and the regular
path, so it would book week 2 instead of skipping it. It might also re-decide
skip versus reject for a taken week. Today the series is booked partially, and
the occurrence index keeps the gap.

### The directive

**The refactor and the exact directive.** Replace Conditional with
Polymorphism. Here is the directive, pasted as I sent it. The scope and the
reason for the boundary are under "Scope."

> Refactor: Replace Conditional with Polymorphism in `src/main/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflow.java`.
>
> The move. Remove the four `switch (type)` statements in `submit` (:55), `cancel` (:187), `priceOf` (:237) and `describe` (:272).
>
> * Add a package-private interface `BookingTypeHandler` in `edu.cmu.cs214.scheduling.workflow`, with one method per operation.
> * Add three package-private classes: `RegularBookingHandler`, `RecurringBookingHandler`, `BlockedBookingHandler`. Move each `case` body into its class verbatim.
> * `BookingWorkflow` keeps the work it does before each switch today (null checks, room lookup, unknown-booking handling, room-name fallback), then delegates to the handler it looks up in an `EnumMap<BookingType, BookingTypeHandler>` built in the constructor.
> * If a type has no handler, return exactly what that method's `default` branch returns today.
>
> Scope.
>
> * In bounds: `workflow/` only. Expected footprint: `BookingWorkflow.java` modified, plus four new files in the same package (the interface and three classes). At most one more package-private helper is allowed, for the facilities address and `recipientFor`.
> * Out of bounds: `domain/`, `notify/`, `pricing/`, `reporting/`, everything under `src/test/`, `pom.xml`, `.github/`, and every `.md` file. Do not add behavior to `BookingType` or `Booking`.
> * The boundary sits here because the type switches exist only in this class. `domain/` is shared with `reporting/`, and `notify/` and `pricing/` are the next two milestones.
>
> Behavior must not change.
>
> * No public signature changes on `BookingWorkflow`, and no test edits.
> * Keep every overlap comparison exactly as written: `<= 0` in the recurring check (:121-122) and `< 0` everywhere else. Do not extract a shared overlap helper.
> * Keep the order of checks and every message string, subject, body and recipient.
> * Keep when ids are drawn: `nextSeriesId()` once before the weekly loop, and `nextBookingId()` once per booking written.
> * Keep the recurring cancel cascading forward from the chosen occurrence, and the recurring `priceOf` summing the whole series.
> * If you see a bug, list it; don't fix it.
>
> Process. Move one booking type at a time and run `mvn -B test` after each step. Stop and report if anything goes red.
>
> Done when:
>
> * `mvn -B test` prints `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`.
> * `git status` shows changes only under `workflow/`.
> * You've shown me `git diff --stat` and the full diff, and listed anything you were unsure about.

### The result

**The diff and the suite.** The diff is commit `81c00d3` (`git show 81c00d3`),
which comes right after the pin commit `9c612da`.
- **Files.** It touches 6 files, all in `workflow/`. `BookingWorkflow.java`
  goes from 296 lines to 106. The new files are `BookingTypeHandler`,
  `RegularBookingHandler`, `RecurringBookingHandler`, `BlockedBookingHandler`,
  and `Recipients`, the one helper the directive allowed.
- **The suite.** I ran it after each booking type moved, and again at the end:

```
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

That's the shipped 35 plus the pin. CI on the fork is green for both commits.

**What did NOT change: behavior and files.**
- **Behavior.** I checked it four ways:
  - The suite, pin included, is green.
  - Each of the 12 moved pieces (4 methods × 3 types) matches its original
    `case` line for line, ignoring indentation. I compared them with `diff`,
    not by eye.
  - A throwaway script (kept locally, not committed) runs every branch of the
    four methods: every rejection, the cancel cascade, and the fallbacks for an
    unknown room or member. It printed the same 232 lines before and after.
  - The pin still guards the new code. Changing `<=` to `<` in
    `RecurringBookingHandler.java:61-62` fails only the pin.
- **The surprises are still there, unchanged.** I listed these and did not fix
  them:
  - Recurring bookings skip the "member already booked elsewhere" check that
    regular bookings make.
  - `priceOf` on a cancelled regular booking still returns the full price.
  - A series with every week taken is rejected but still uses up a series id.
- **Files.** This command prints nothing:
  `git diff --exit-code 9c612da 81c00d3 -- src/test src/main/java/edu/cmu/cs214/scheduling/{domain,notify,pricing,reporting} pom.xml .github README.md REFACTOR.md SETUP.md`.
  The agent stayed inside `workflow/`. The only extra file is `Recipients`,
  which the directive allowed.

**One thing the agent changed that you had to look at twice.** The old
`default:` branches. All three types are registered in the `EnumMap`, so a
missing handler can't happen, and deleting the fallback looks like harmless
tidying. But removing code that "never runs" is one way a cleanup quietly
changes behavior, so the directive said to keep it. I checked each of the four
new `if (handler == null)` returns against its old `default`, and all four
match:
- `submit` still rejects with `unsupported booking type …`.
- `cancel` still returns `false`.
- `priceOf` still returns `0.0`.
- `describe` still returns `Booking #<id> in <room>`.

### The closing explanation

**Refactor or regenerate?** Refactoring was the better call.
- **Test coverage.** The 18 workflow tests check about one happy path or one
  rejection per type. A lot is unpinned:
  - the recurring boundary, until my pin;
  - what cancelling a middle occurrence does (`recurringCancelReleasesTheOccurrence`
    cancels the last one);
  - the missing member check on recurring bookings;
  - the price of a cancelled booking.

  A regenerated class only has to pass what's pinned, so it would quietly
  re-decide all of that. A move makes no new decisions, so the same tests plus
  the pin were enough to check it.
- **Code age.** One agent-generated commit, with no history of bug fixes. This
  is the one factor that leans toward regenerating, because there's little
  hard-won knowledge to lose. But the quirks above are still decisions, just
  ones nobody wrote down.
- **Spec quality.** The spec is one README sentence plus some javadoc, and it
  disagrees with the code. `TimeSlot` says the end is exclusive; the recurring
  check treats it as inclusive. Regenerating from that spec would produce
  different behavior.
- **Reach.** The README says every store write and every notification goes
  through this class.
  - `ReportService` reads what it writes (types, series ids, cancelled flags)
    for revenue and occupancy.
  - `NotificationHubTest` checks its exact message text.
  - Members receive the messages.

  A slip here spreads to all of them.

The structure was also mostly right: one front door, with only the type switch
repeated. A move fixed that, and checking it meant comparing 12 pieces against
their originals. A regeneration would have reset the review to every line.

**What would flip your answer.** Two conditions together:
- every unpinned behavior listed above had a test;
- the spec stated the two rules the code leaves unwritten: whether a booking
  that only touches another one is a conflict, and whether cancelling one
  occurrence also cancels the later ones.

Then the tests and the spec could check a fresh implementation on their own,
and regenerating would cost about what this refactor did.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

- **Singleton:** `NotifierFactory` (private constructor, `getInstance()`).
- **Factory:** `NotifierFactory.createStrategy()`.
- **Strategy:** `NotificationStrategy`, implemented by
  `EmailNotificationStrategy` and held by `NotificationHub`.
- **Observer:** `NotificationHub` publishes to `NotificationSubscriber`s.
  `OutboxSubscriber` is the only one.
- **Adapter (a small one):** `OutboxSubscriber` wraps `Outbox` so it fits the
  subscriber interface.

### The problem each one solves

- **Singleton:** there is one shared thing with state, such as a mail
  connection or a send quota, and every part of the program must use the same
  copy.
- **Factory:** more than one kind of object can be built, and the rule for
  picking one (a setting, a member's preference) should live in one place
  instead of in every caller.
- **Strategy:** there are several ways to do the same job, like formatting a
  message for email or for SMS, and the program picks between them while it
  runs.
- **Observer:** one event has several independent reactions, and the code that
  raises the event shouldn't have to know who reacts.
- **Adapter:** a class you already have doesn't fit an interface that other
  code requires.

### Which of those problems exist here

None of them.
- **Singleton:** no. `NotifierFactory` has no state to share; its only field is
  the instance itself (`NotifierFactory.java:6`). The only thing that cares
  there is one copy is the test `factoryHandsBackTheSameInstance`.
- **Factory:** no. `createStrategy()` takes no input and always returns
  `new EmailNotificationStrategy()` (`NotifierFactory.java:19-21`). Its one
  caller is `NotificationHub.java:22`.
- **Strategy:** no. There is one implementation, and it couldn't be swapped
  anyway: the hub picks it itself at `NotificationHub.java:22`, and no
  constructor accepts one, so not even a test can pass in another.
- **Observer:** no. There is one subscriber, added by the hub's own constructor
  (`NotificationHub.java:23`). Nothing else calls `subscribe`, and
  `hubDeliversToItsOneSubscriber` asserts there is exactly one.
- **Adapter:** no. `OutboxSubscriber` exists only to satisfy the Observer
  interface; `Outbox.append` already does the job.

So `notify/` uses four patterns to put one formatted line into one list.

### The simpler structure

**Your proposal.** Keep three classes: `NotificationHub`, `NotificationMessage`
and `Outbox`. The hub keeps its name, both constructors and `getOutbox()`, so
`BookingWorkflow` and the tests don't change. `publish` does the work itself:

```java
public void publish(NotificationMessage m) {
    outbox.append("To: " + m.recipient() + " | Subject: " + m.subject() + " | " + m.body());
}
```

Delete `NotifierFactory`, `NotificationStrategy`, `EmailNotificationStrategy`,
`NotificationSubscriber` and `OutboxSubscriber`. Eight files become three.

**What stays the same.**
- The text is exactly `To: <recipient> | Subject: <subject> | <body>`, as
  `publishedMessageLandsInTheOutboxFullyRendered` and
  `aConfirmationFromTheWorkflowReachesTheOutbox` check.
- There is one outbox entry per `publish`, in order. Every
  `hub.getOutbox().size()` check in `BookingWorkflowTest` covers this, for
  example 2 in `regularCancelReleasesTheSlotAndNotifies` and 4 in
  `recurringSubmitBooksEveryWeekOfAnOpenSeries`.
- `NotificationMessage` still rejects a blank recipient or a null subject.
- Two shipped tests check the structure, not the behavior:
  `hubDeliversToItsOneSubscriber` and `factoryHandsBackTheSameInstance`. They
  would go with the layers they test, which is why this stays a proposal: we
  can't delete shipped tests.

**What you would keep, if anything.** None of the interfaces. The one
interface usually worth keeping in a setup like this sits in front of the mail
vendor, so tests can swap in a fake. There is no vendor here: `Outbox` is in
memory and the tests read it directly. `Outbox` stays as a plain class because
it's what the tests check.

### What would bring each layer back

- **Strategy:** members can choose SMS instead of email, and an SMS needs a
  short text with no subject line. That's two formats that change for
  different reasons.
- **Factory:** the channel is picked by rules (the member's preference, or a
  daily digest during quiet hours), and more than one place sends
  notifications. That rule belongs in one place.
- **Observer:** every notification must also go to an audit log and to the
  front desk's screen, not only to the outbox. That's several independent
  receivers for each message.

**Misuse or anti-pattern?** Misuse. Each one is a sound pattern; the problem it
solves just isn't here, so the critique is "not here."

The distinction matters because the fix is different:
- **Misuse:** delete it now and add it back when the requirement arrives. That
  is cheap, because the tests pin the behavior.
- **Anti-pattern:** it is harmful even when the problem is real, so you would
  replace it with something else.

The closest thing to an anti-pattern here is the hub grabbing a global
singleton inside its own constructor. That hidden dependency is why the
strategy can't be swapped, even in a test. If new channels do arrive, pass the
strategy in rather than bring the singleton back.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Decorator fits `PriceCalculator`: a slot's price is a base
hourly amount passed through independent adjustments that all apply in turn
(weekend surcharge, long-booking discount, tier discount), so each rule could
wrap the price before it behind the same interface, and adding a rule would be
a new class instead of an edit to the one tested `price()` method.

**Would you apply it today?** No: four stable rules in one short, readable
method, all pinned by six tests and used only inside this repo, so it can wait
and be added as a preparatory refactoring when a new rule (such as a flat fee)
actually arrives.

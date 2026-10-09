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

**The refactor and the exact directive.** Name the refactor (one from the menu
in the handout) and paste the directive you gave the agent, including the scope
you set, meaning which files and packages were in bounds, which were not, and
one line on why the boundary sits where it does.

### The result

**The diff and the suite.** How you are showing the diff to the TA (a commit,
`git diff`, a branch), and the totals line (the shipped count plus your pin,
all green).

**What did NOT change: behavior and files.** The observable behavior you
checked is still the same, including anything that surprised you while reading.
Which files outside the scope are untouched, and how you verified that rather
than assumed it. If the agent reached outside the directive, say where and what
you did about it.

**One thing the agent changed that you had to look at twice.** Something you
checked line by line before accepting. If there was nothing, say how carefully
you read the diff.

### The closing explanation

**Refactor or regenerate?** Argue whether regenerating `BookingWorkflow` from scratch
would have been the better call, using the lecture's four questions (test
coverage, code age, spec quality, and reach). Be concrete about this codebase.

**What would flip your answer.** A condition about the artifact, not a feeling.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

List every design pattern you can name in that package. For each one, the class
or classes that carry it.

### The problem each one solves

For each pattern you listed, what would have to be true about the requirements
for that pattern to be the right call? One sentence each, not in terms of
"flexibility".

### Which of those problems exist here

For each pattern, does the problem it solves exist in this codebase? Point at
the code that settles it.

### The simpler structure

**Your proposal.** What replaces `notify/`. Sketch the classes and the one
method that matters.

**What stays the same.** The tested behavior it must still produce, named
precisely enough that a reader can check it against the shipped tests.

**What you would keep, if anything.** If you would keep one interface, say
which and why. "None of it" is a fine answer if you can defend it.

### What would bring each layer back

For at least two of the layers you would remove, what requirement, if it
arrived next sprint, would make that layer the right structure? Be specific
about the requirement, not about the pattern.

**Misuse or anti-pattern?** Say which this is and why the distinction matters.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

**Would you apply it today?** Yes or no, one line, with the reason.

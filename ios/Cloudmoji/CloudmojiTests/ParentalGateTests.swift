import Testing
@testable import Cloudmoji

/// The gate's arithmetic, which is the whole of its security model.
///
/// There is nothing secret behind this door — only settings a child should not be
/// able to change — so the property being defended is narrow and exact: a wrong
/// answer must not open it, and no answer must be reachable by a two-year-old
/// mashing a numeric keypad. Whether the door itself is wired to Settings is a
/// question about a real accessibility tree and is answered in
/// `ParentalGateUITests`.
@Suite("GateChallenge")
@MainActor
struct ParentalGateTests {

    /// The whole point of the gate: a wrong answer does not get through. It is the
    /// single most droppable line in the file — a gate that accepts anything still
    /// looks exactly like a gate, and the spec lists "the parental gate blocks a
    /// wrong answer" as a behaviour to regression-test.
    ///
    /// Mutation: delete `value == answer` and return `true`. Run and confirmed
    /// failing on "57" and "-56" — and only those two, which is the useful part.
    /// "", "abc" and "5 6" are all still rejected by the `Int(_:)` guard one line
    /// above, so a test built only from junk input would pass a gate that opens
    /// for every number a parent could type. The two wrong *numbers* are the
    /// expectations doing the work here.
    @Test("only the right answer is accepted")
    func acceptsOnlyTheProduct() {
        let challenge = GateChallenge(a: 7, b: 8)
        #expect(challenge.answer == 56)
        #expect(challenge.accepts("56"))
        #expect(challenge.accepts(" 56 "), "a trailing space from a numeric keypad is not a wrong answer")
        #expect(challenge.accepts("57") == false)
        #expect(challenge.accepts("") == false, "an empty field must not open the gate")
        #expect(challenge.accepts("abc") == false)
        #expect(challenge.accepts("5 6") == false)
        // The web spells this `Number(entry) === a * b`, where `Number("")` is 0
        // and `Number("-56")` is -56. Neither is an answer to "what is 7 × 8".
        #expect(challenge.accepts("-56") == false)
    }

    /// Mutation: delete the `% all.count`. Run and confirmed failing — though it
    /// is caught as a *trap*, "Fatal error: Index out of range", not as an
    /// expectation. That is the defect itself: the ninth time a parent opens
    /// Settings in one session the app dies in front of the child. The run is
    /// reported as `** TEST FAILED **` but the restarted summary line reads
    /// "0 tests passed", so read the failing-test list, never the count alone.
    @Test("challenges rotate and wrap")
    func challengesRotate() {
        #expect(GateChallenge.all.count == 8)
        #expect(GateChallenge.at(0) != GateChallenge.at(1))
        #expect(GateChallenge.at(8) == GateChallenge.at(0), "the rotation must wrap, not run off the end")
        #expect(GateChallenge.at(9) == GateChallenge.at(1))
    }

    /// The gate has to survive a two-year-old holding the phone and hitting the
    /// keypad. Every product is two digits and neither factor is trivial, so mashing
    /// has effectively no chance — which is the actual security model here, since
    /// there is nothing secret behind it, only settings a child should not change.
    ///
    /// The count is asserted **inside this test** rather than left to
    /// `challengesRotate`: a loop over an empty table passes every property it is
    /// given, and that is the vacuous-pass shape this project keeps finding.
    ///
    /// Mutation: add a `(2, 3)` challenge to the table. This fails.
    @Test("no challenge is answerable by accident")
    func challengesAreNotTrivial() {
        #expect(GateChallenge.all.count == 8, "an empty table would satisfy every check below")
        for challenge in GateChallenge.all {
            #expect(challenge.answer >= 40, "\(challenge.a) x \(challenge.b) is too easy to hit by chance")
            #expect(challenge.a >= 6 && challenge.b >= 6)
        }
    }
}

package Controller;

import Model.PlayerColor;
import Model.PlayerSetup;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SetupControllerTest {
    @Test
    void acceptsTwoThreeAndFourPlayersPreservingNameColorPairs() {
        List<String> names = List.of("Razi", "Roman", "Maor", "Fourth");
        List<String> colors = List.of("Blue", "Red", "Green", "Yellow");
        for (int count = 2; count <= 4; count++) {
            var result = SetupController.validateSetup(names.subList(0, count), colors.subList(0, count));
            assertTrue(result.errors().isEmpty());
            assertEquals(count, result.players().size());
            assertEquals(new PlayerSetup("Razi", PlayerColor.BLUE), result.players().getFirst());
            assertEquals(new PlayerSetup("Roman", PlayerColor.RED), result.players().get(1));
        }
    }

    @Test
    void normalizesNamesAndColorsWithoutChangingTheFormValues() {
        List<String> names = new ArrayList<>(List.of("  Razi  ", " Roman "));
        List<String> colors = new ArrayList<>(List.of(" blue ", "rED"));
        var result = SetupController.validateSetup(names, colors);
        assertTrue(result.errors().isEmpty());
        assertEquals(List.of(new PlayerSetup("Razi", PlayerColor.BLUE), new PlayerSetup("Roman", PlayerColor.RED)), result.players());
        assertEquals(List.of("  Razi  ", " Roman "), names);
        assertEquals(List.of(" blue ", "rED"), colors);
        names.clear(); colors.clear();
        assertEquals(2, result.players().size());
        assertThrows(UnsupportedOperationException.class, () -> result.players().clear());
    }

    @Test
    void reportsEveryInvalidNameWithItsPlayerNumberAndReturnsNoPartialSetup() {
        var result = SetupController.validateSetup(List.of("  ", "Ra,zi"), List.of("Red", "Blue"));
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().get(0).startsWith("Player 1:"));
        assertTrue(result.errors().get(1).startsWith("Player 2:"));
        assertTrue(result.players().isEmpty());
    }

    @Test
    void rejectsDuplicateNamesAfterTrimmingIgnoringCase() {
        var result = SetupController.validateSetup(List.of(" Razi ", "razi"), List.of("Red", "Blue"));
        assertEquals(List.of("Please use unique player names"), result.errors());
        assertTrue(result.players().isEmpty());
    }

    @Test
    void rejectsDuplicateColorsAndCanReportBothDuplicateErrors() {
        var result = SetupController.validateSetup(List.of("Razi", "Roman"), List.of("RED", "red"));
        assertEquals(List.of("Please use unique player colors"), result.errors());
        var both = SetupController.validateSetup(List.of("Razi", "razi"), List.of("Red", "red"));
        assertEquals(List.of("Please use unique player names", "Please use unique player colors"), both.errors());
        assertTrue(both.players().isEmpty());
    }

    @Test
    void reportsUnknownColorAsUserInputError() {
        var result = SetupController.validateSetup(List.of("Razi", "Roman"), List.of("Purple", "Red"));
        assertEquals(List.of("Player 1: Please select Red, Green, Yellow or Blue"), result.errors());
        assertTrue(result.players().isEmpty());
    }

    @Test
    void rejectsMissingColorPairAndParticipantCountsOutsideTwoToFour() {
        var mismatch = SetupController.validateSetup(List.of("Razi", "Roman"), List.of("Red"));
        assertEquals(List.of("Each player needs exactly one color"), mismatch.errors());
        assertTrue(mismatch.players().isEmpty());
        for (int count : new int[]{0, 1, 5}) {
            List<String> names = new ArrayList<>();
            List<String> colors = new ArrayList<>();
            for (int i = 0; i < count; i++) { names.add("Player " + i); colors.add("Red"); }
            var result = SetupController.validateSetup(names, colors);
            assertEquals(List.of("Please add between 2 and 4 players"), result.errors());
            assertTrue(result.players().isEmpty());
        }
    }

    @Test
    void correctedInputIsAcceptedAfterAnEarlierFailedAttempt() {
        List<String> names = new ArrayList<>(List.of("", "Roman"));
        List<String> colors = List.of("Green", "Red");
        var invalid = SetupController.validateSetup(names, colors);
        names.set(0, "Razi");
        var corrected = SetupController.validateSetup(names, colors);
        assertFalse(invalid.errors().isEmpty());
        assertTrue(corrected.errors().isEmpty());
        assertEquals("Razi", corrected.players().getFirst().name());
    }
}

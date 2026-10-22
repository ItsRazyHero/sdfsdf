package io.github.itsrazyhero.menuforge;

import io.github.itsrazyhero.menuforge.flow.Block;
import io.github.itsrazyhero.menuforge.flow.Blocks;
import io.github.itsrazyhero.menuforge.flow.FlowParser;
import io.github.itsrazyhero.menuforge.util.Durations;
import io.github.itsrazyhero.menuforge.util.Text;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Pure-logic tests that need no running server. */
class CoreTest {

    @Test
    void durations() {
        assertEquals(86_400_000L, Durations.parseMillis("1d"));
        assertEquals(90_000L, Durations.parseMillis("1m30s"));
        assertEquals(20_000L, Durations.parseMillis("20"));
        assertEquals(1_000L, Durations.parseMillis("20t"));
        assertEquals("1d 2h 0m 5s", Durations.format(93_605_000L, "{d}d {h}h {m}m {s}s"));
        assertEquals("5s", Durations.format(5_000L, "{d}d {h}h {m}m {s}s"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parseMillis("soon"));
    }

    @Test
    void legacyToMiniMessage() {
        assertEquals("<green>Hi <bold>there", Text.legacyToMini("&aHi &lthere"));
        assertEquals("<#ff8c1a>x", Text.legacyToMini("&#ff8c1ax"));
        assertEquals("<red>already mini", Text.legacyToMini("<red>already mini"));
        assertEquals("Hi there", Text.plain("&aHi <bold>there"));
    }

    @Test
    void shorthandParsing() {
        Map<String, Object> m = FlowParser.fromShorthand("[console] give %player_name% diamond");
        assertEquals("console", m.get("type"));
        assertEquals("give %player_name% diamond", m.get("command"));

        m = FlowParser.fromShorthand("[sound] entity.player.levelup;0.5;2");
        assertEquals(0.5f, m.get("volume"));
        assertEquals(2f, m.get("pitch"));

        m = FlowParser.fromShorthand("[open] shop diamond");
        assertEquals("shop", m.get("menu"));
        assertEquals("diamond", m.get("args"));

        m = FlowParser.fromShorthand("plain text");
        assertEquals("message", m.get("type"));
    }

    @Test
    void parserBuildsBlocksAndDenyBranches() {
        List<String> warnings = new ArrayList<>();
        FlowParser parser = new FlowParser(warnings::add);
        List<Block> flow = parser.parse(List.of(
                Map.of("type", "cooldown", "key", "k", "duration", "1d", "deny", List.of("[message] wait")),
                "[console] say hi",
                "[delay] 20",
                "[close]",
                Map.of("type", "nope")));
        assertEquals(4, flow.size());
        assertInstanceOf(Blocks.Cooldown.class, flow.get(0));
        assertInstanceOf(Blocks.ConsoleCommand.class, flow.get(1));
        assertEquals(new Blocks.Delay(20), flow.get(2));
        assertInstanceOf(Blocks.Close.class, flow.get(3));
        assertEquals(1, warnings.size());
    }
}

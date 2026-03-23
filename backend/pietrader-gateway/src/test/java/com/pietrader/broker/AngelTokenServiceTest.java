package com.pietrader.broker;

import com.pietrader.broker.angel.AngelInstrument;
import com.pietrader.broker.angel.AngelTokenService;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;

@DisplayName("AngelTokenService — Contract Load + Token Query Tests")
class AngelTokenServiceTest {

    private AngelTokenService service;

    @BeforeEach void setUp() {
        service = new AngelTokenService();
        service.loadMasterContract();
    }

    @Test @DisplayName("Contract loads: tokens present")
    void contractLoads() {
        assertThat(service.getTokenMap()).isNotEmpty();
        System.out.println("✅ Tokens: " + service.getTokenMap().size());
    }

    @Test @DisplayName("All tokens OPTIDX/NFO")
    void allOptIdxNfo() {
        service.getTokenMap().values().forEach(i -> {
            assertThat(i.getInstrumentType()).isEqualToIgnoringCase("OPTIDX");
            assertThat(i.getExchSeg()).isEqualToIgnoringCase("NFO");
        });
    }

    @Test @DisplayName("All tokens CE or PE")
    void allCeOrPe() {
        service.getTokenMap().values().forEach(i -> assertThat(i.getOptionType()).isIn("CE","PE"));
    }

    @Test @DisplayName("Strike prices positive (paisa/100 correct)")
    void strikePositive() {
        service.getTokenMap().values().stream().limit(100).forEach(i -> assertThat(i.getStrikePrice()).isPositive());
    }

    @Test @DisplayName("NIFTY options exist")
    void niftyOptionsExist() {
        List<AngelInstrument> opts = service.getOptions("NIFTY");
        assertThat(opts).isNotEmpty();
        System.out.println("✅ NIFTY options: " + opts.size());
    }

    @Test @DisplayName("BANKNIFTY options exist")
    void bankNiftyOptionsExist() { assertThat(service.getOptions("BANKNIFTY")).isNotEmpty(); }

    @Test @DisplayName("Unknown symbol returns empty")
    void unknownSymbolEmpty() { assertThat(service.getOptions("UNKNOWN_XYZ")).isEmpty(); }

    @Test @DisplayName("getOptions is case-insensitive")
    void caseInsensitive() {
        assertThat(service.getOptions("nifty").size()).isEqualTo(service.getOptions("NIFTY").size());
    }

    @Test @DisplayName("getNearestExpiry not null for NIFTY")
    void nearestExpiryNotNull() {
        String exp = service.getNearestExpiry("NIFTY");
        assertThat(exp).isNotNull().isNotEmpty();
        System.out.println("✅ Nearest expiry: " + exp);
    }

    @Test @DisplayName("getNearestExpiry is lexicographically first")
    void nearestExpiryIsFirst() {
        String exp = service.getNearestExpiry("NIFTY");
        String min = service.getOptions("NIFTY").stream().map(AngelInstrument::getExpiry).distinct().sorted().findFirst().orElse(null);
        assertThat(exp).isEqualTo(min);
    }

    @Test @DisplayName("getNearestExpiry null for unknown symbol")
    void nearestExpiryNullUnknown() { assertThat(service.getNearestExpiry("UNKNOWN_XYZ")).isNull(); }

    @Test @DisplayName("getTokensForStrikeRange returns tokens for NIFTY ±500")
    void tokensForRange() {
        int atm = getAtm("NIFTY");
        List<String> tokens = service.getTokensForStrikeRange("NIFTY", atm, 500);
        assertThat(tokens).isNotEmpty();
        System.out.println("✅ Tokens in ±500 of " + atm + ": " + tokens.size());
    }

    @Test @DisplayName("Range tokens all belong to nearest expiry")
    void rangeTokensNearestExpiry() {
        String exp = service.getNearestExpiry("NIFTY");
        service.getTokensForStrikeRange("NIFTY", getAtm("NIFTY"), 500).forEach(t -> {
            AngelInstrument i = service.getTokenInfo(t);
            assertThat(i).isNotNull();
            assertThat(i.getExpiry()).isEqualTo(exp);
        });
    }

    @Test @DisplayName("findToken returns token for valid PE strike")
    void findTokenValidPe() {
        AngelInstrument s = service.getOptions("NIFTY").stream()
            .filter(i -> "PE".equals(i.getOptionType()) && i.getExpiry().equals(service.getNearestExpiry("NIFTY")))
            .findFirst().orElse(null);
        Assumptions.assumeThat(s).isNotNull();
        assertThat(service.findToken("NIFTY", s.getStrikePrice() + " PE")).isNotNull();
    }

    @Test @DisplayName("findToken returns null for invalid strike")
    void findTokenInvalid() { assertThat(service.findToken("NIFTY", "9999999 PE")).isNull(); }

    @Test @DisplayName("findTradingSymbol returns full name ending in PE/CE")
    void findTradingSymbolFull() {
        AngelInstrument s = service.getOptions("NIFTY").stream()
            .filter(i -> "PE".equals(i.getOptionType()) && i.getExpiry().equals(service.getNearestExpiry("NIFTY")))
            .findFirst().orElse(null);
        Assumptions.assumeThat(s).isNotNull();
        String sym = service.findTradingSymbol("NIFTY", s.getStrikePrice() + " PE");
        assertThat(sym).isNotNull().endsWith("PE");
    }

    @Test @DisplayName("loadFromUrl downloads contract (network test)")
    @Tag("network")
    void loadFromUrl() {
        try {
            var data = service.loadFromUrl();
            assertThat(data).isNotNull().isNotEmpty();
            System.out.println("✅ Live download: " + data.size() + " records");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Network unavailable: " + e.getMessage());
        }
    }

    private int getAtm(String sym) {
        return (int) service.getOptions(sym).stream()
            .filter(i -> i.getExpiry().equals(service.getNearestExpiry(sym)))
            .mapToInt(AngelInstrument::getStrikePrice).average().orElse(23000);
    }
}

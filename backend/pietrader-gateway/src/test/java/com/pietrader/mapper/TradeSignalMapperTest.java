package com.pietrader.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.dto.*;
import com.pietrader.dto.dealer.*;
import com.pietrader.dto.decision.*;
import com.pietrader.dto.execution.*;
import com.pietrader.dto.liquidity.*;
import com.pietrader.dto.market.*;
import com.pietrader.dto.signal.TradeSignalDTO;
import com.pietrader.dto.volatility.*;
import com.pietrader.entity.TradeSignalEntity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeSignalMapper — DTO to Entity Mapping Tests")
class TradeSignalMapperTest {

    private TradeSignalMapper mapper;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, true);
        mapper = new TradeSignalMapper(objectMapper);
    }

    // ── NULL GUARD ────────────────────────────────────────────────────────────

    @Test @DisplayName("null DTO returns null entity")
    void nullDto_returnsNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test @DisplayName("empty DTO does not throw")
    void emptyDto_doesNotThrow() {
        assertThatCode(() -> mapper.toEntity(new OptionAnalyticsDTO())).doesNotThrowAnyException();
    }

    // ── MARKET CONTEXT ────────────────────────────────────────────────────────

    @Test @DisplayName("Maps market_context fields correctly")
    void mapsMarketContext() {
        OptionAnalyticsDTO dto = buildFull();
        TradeSignalEntity e = mapper.toEntity(dto);

        assertThat(e.getSymbol()).isEqualTo("NIFTY");
        assertThat(e.getSpot()).isEqualTo(23114.5);
        assertThat(e.getAtm()).isEqualTo(23100);
        assertThat(e.getExpiry()).isEqualTo("24MAR2026");
        assertThat(e.getDaysToExpiry()).isEqualTo(4);
        assertThat(e.getPhase()).isEqualTo("EXPIRY_WEEK");
    }

    // ── TRADE SIGNAL ──────────────────────────────────────────────────────────

    @Test @DisplayName("Maps trade_signal fields correctly")
    void mapsTradeSignal() {
        TradeSignalEntity e = mapper.toEntity(buildFull());
        assertThat(e.getStrategy()).isEqualTo("TREND_SELL");
        assertThat(e.getSignalReason()).contains("Bearish");
        assertThat(e.getSignalConfidence()).isEqualTo("MEDIUM");
    }

    // ── AUTO TRADE DECISION ───────────────────────────────────────────────────

    @Test @DisplayName("Maps auto_trade_decision.action correctly")
    void mapsAction() {
        TradeSignalEntity e = mapper.toEntity(buildFull());
        assertThat(e.getAction()).isEqualTo("EXECUTE");
        assertThat(e.getDirection()).isEqualTo("DOWN");
        assertThat(e.getOptionStrike()).isEqualTo("23000 PE");
        assertThat(e.getDecisionConfidence()).isEqualTo("MEDIUM");
    }

    // ── DEALER POSITIONING ────────────────────────────────────────────────────

    @Test @DisplayName("Maps gamma_flip as Double (not Integer)")
    void mapsGammaFlipAsDouble() {
        TradeSignalEntity e = mapper.toEntity(buildFull());
        assertThat(e.getGammaFlip()).isNotNull();
        assertThat(e.getGammaFlip()).isEqualTo(23150.0);
        assertThat(e.getDealerInventory()).isEqualTo("SHORT_GAMMA");
    }

    @Test @DisplayName("gamma_flip=23150.0 (float in JSON) does NOT null dealer section")
    void gammaFlipFloat_doesNotNullDealerSection() {
        OptionAnalyticsDTO dto = buildFull();
        TradeSignalEntity e = mapper.toEntity(dto);
        // If DealerInventoryModelDTO was null, gammaFlip would be null
        assertThat(e.getGammaFlip()).as("gammaFlip must not be null — OPTIDX float issue").isNotNull();
        assertThat(e.getNetGamma()).isNotNull();
    }

    // ── VOLATILITY ────────────────────────────────────────────────────────────

    @Test @DisplayName("Maps volatility_context correctly")
    void mapsVolatility() {
        TradeSignalEntity e = mapper.toEntity(buildFull());
        assertThat(e.getAtmIvPct()).isEqualTo(23.37);
        assertThat(e.getDailyMove()).isEqualTo(340.29);
        assertThat(e.getPcr()).isEqualTo(1.002);
        assertThat(e.getPcrSentiment()).isEqualTo("neutral");
    }

    // ── FEAR INDEX ────────────────────────────────────────────────────────────

    @Test @DisplayName("Maps fear_index_analysis correctly")
    void mapsFearIndex() {
        TradeSignalEntity e = mapper.toEntity(buildFull());
        assertThat(e.getFearIndex()).isEqualTo(65.6);
        assertThat(e.getFearZone()).isEqualTo("NEUTRAL");
    }

    // ── EXECUTION ─────────────────────────────────────────────────────────────

    @Test @DisplayName("Maps execution_layer fields correctly")
    void mapsExecution() {
        TradeSignalEntity e = mapper.toEntity(buildFull());
        assertThat(e.getExecutionReady()).isTrue();
        assertThat(e.getEntrySignal()).isEqualTo("ENTER_SHORT");
    }

    // ── LIQUIDITY ─────────────────────────────────────────────────────────────

    @Test @DisplayName("Maps support and resistance correctly")
    void mapsLiquidity() {
        TradeSignalEntity e = mapper.toEntity(buildFull());
        assertThat(e.getSupport()).isEqualTo(23000);
        assertThat(e.getResistance()).isEqualTo(23800);
    }

    // ── FULL ROUND-TRIP ───────────────────────────────────────────────────────

    @Test @DisplayName("Full DTO: no null critical fields after mapping")
    void fullDto_noCriticalNulls() {
        TradeSignalEntity e = mapper.toEntity(buildFull());
        assertThat(e.getSymbol()).isNotNull();
        assertThat(e.getSpot()).isNotNull();
        assertThat(e.getAction()).isNotNull();
        assertThat(e.getDirection()).isNotNull();
        assertThat(e.getOptionStrike()).isNotNull();
        assertThat(e.getFearIndex()).isNotNull();
        assertThat(e.getGammaFlip()).isNotNull();
        assertThat(e.getAtmIvPct()).isNotNull();
    }

    // ── BUILDER ───────────────────────────────────────────────────────────────
    private OptionAnalyticsDTO buildFull() {
        // Market context
        ExpiryDTO expiry = new ExpiryDTO();
        expiry.setNearestExpiry("24MAR2026"); expiry.setDaysToExpiry(4); expiry.setPhase("EXPIRY_WEEK");
        SessionDTO session = new SessionDTO(); session.setIsMarket(true); session.setSession("MARKET");
        MarketContextDTO mkt = new MarketContextDTO();
        mkt.setSymbol("NIFTY"); mkt.setSpot(23114.5); mkt.setAtm(23100);
        mkt.setExpiry(expiry); mkt.setSession(session);

        // Trade signal
        TradeSignalDTO ts = new TradeSignalDTO();
        ts.setStrategy("TREND_SELL"); ts.setReason("Bearish pressure"); ts.setConfidence("MEDIUM");

        // Auto trade decision
        AutoTradeActionDTO action = new AutoTradeActionDTO();
        action.setAction("EXECUTE"); action.setOption("23000 PE");
        action.setDirection("DOWN"); action.setConfidence("MEDIUM");
        AutoTradeDecisionDTO atd = new AutoTradeDecisionDTO();
        atd.setAutoTradeAction(action);

        // Dealer positioning
        DealerInventoryModelDTO dim = new DealerInventoryModelDTO();
        dim.setDealerInventory("SHORT_GAMMA"); dim.setGammaFlip(23150.0); dim.setNetGamma(-58.27);
        DealerPositioningDTO dp = new DealerPositioningDTO();
        dp.setDealerInventoryModel(dim);

        // Volatility
        VolatilityEngineDTO ve = new VolatilityEngineDTO();
        ve.setAtmIvPct(23.37); ve.setDailyMove(340.29);
        PcrDTO pcr = new PcrDTO(); pcr.setPcr(1.002); pcr.setSentiment("neutral");
        VolatilityContextDTO vc = new VolatilityContextDTO();
        vc.setVolatilityEngine(ve); vc.setPcr(pcr);

        // Execution
        FinalExecutionDTO fe = new FinalExecutionDTO();
        fe.setExecutionReady(true);
        ExecutionLayerDTO el = new ExecutionLayerDTO(); el.setFinalExecution(fe);
        ExecutionTimingDTO et = new ExecutionTimingDTO(); et.setEntrySignal("ENTER_SHORT");

        // Fear index
        FearIndexAnalysisDTO fi = new FearIndexAnalysisDTO();
        fi.setCurrentFearIndex(65.6); fi.setZone("NEUTRAL");
        CompleteDecisionDTO cd = new CompleteDecisionDTO(); cd.setFearIndexAnalysis(fi);

        // Liquidity
        SupportResistanceDTO sr = new SupportResistanceDTO();
        sr.setSupport(23000); sr.setResistance(23800);
        LiquidityMapDTO lm = new LiquidityMapDTO(); lm.setSupportResistance(sr);

        // Confidence
        ConfidenceDTO conf = new ConfidenceDTO(); conf.setConfidenceScore(75);

        return OptionAnalyticsDTO.builder()
            .marketContext(mkt).tradeSignal(ts).autoTradeDecision(atd)
            .dealerPositioning(dp).volatilityContext(vc).executionLayer(el)
            .executionTiming(et).completeDecision(cd).liquidityMap(lm)
            .confidence(conf).build();
    }
}

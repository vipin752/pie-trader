package com.pietrader.contract;

import com.pietrader.dto.ConfidenceDTO;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.dealer.DealerInventoryModelDTO;
import com.pietrader.dto.dealer.DealerPositioningDTO;
import com.pietrader.dto.dealer.GammaDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import com.pietrader.dto.decision.AutoTradeDecisionDTO;
import com.pietrader.dto.decision.CompleteDecisionDTO;
import com.pietrader.dto.decision.FearIndexAnalysisDTO;
import com.pietrader.dto.execution.ExecutionLayerDTO;
import com.pietrader.dto.execution.ExecutionTimingDTO;
import com.pietrader.dto.execution.FinalExecutionDTO;
import com.pietrader.dto.market.MarketContextDTO;
import com.pietrader.dto.market.MarketStructureDTO;
import com.pietrader.dto.market.CompressionDTO;
import com.pietrader.dto.market.SessionDTO;
import com.pietrader.dto.signal.TradeSignalDTO;
import com.pietrader.dto.volatility.PcrDTO;
import com.pietrader.dto.volatility.HistoricalContextDTO;
import com.pietrader.dto.volatility.VolatilityContextDTO;
import com.pietrader.model.RawOptionChainRecord;
import com.pietrader.model.StrikeData;
import com.pietrader.model.TradeDecision;
import com.pietrader.state.TradeState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PIE TRADER — DataContractValidationTest (fixed)
 *
 * Verifies actual DTO field existence using real object construction.
 * Every getter call compiles against the real DTO classes — if a field
 * doesn't exist the test won't compile.
 *
 * Key corrections vs previous version:
 *   ❌ MarketContextDTO.getRegime()        → NOT a field on MarketContextDTO
 *   ✅ HistoricalContextDTO.getRegime()    → correct location
 *
 *   ❌ VolatilityContextDTO.getIvRegime()  → NOT a field
 *   ✅ DealerInventoryModelDTO.getVolatilityRegime() → correct
 *
 *   ❌ SessionDTO.getSessionPhase()        → NOT a field
 *   ✅ SessionDTO.getSession()             → correct field name
 *
 *   ❌ PcrDTO.getPcrValue()               → NOT a field
 *   ✅ PcrDTO.getPcr()                    → correct field name
 *
 *   ❌ DealerInventoryModelDTO.getGammaFlip() treated as Boolean
 *   ✅ getGammaFlip() returns Double (the flip price level)
 *
 *   ❌ TradingCardDTO.getRationale()       → NOT a field
 *   ✅ AutoTradeActionDTO.getReason()      → correct
 */
@DisplayName("Data Contract — DTO Field Validation (Compile-time Safe)")
class DataContractValidationTest {

    // ── OptionAnalyticsDTO top-level fields ───────────────────────────────────

    @Nested @DisplayName("OptionAnalyticsDTO — top-level fields")
    class OptionAnalyticsDTOTest {

        @Test @DisplayName("All used fields exist and are gettable")
        void fieldsExist() {
            OptionAnalyticsDTO dto = new OptionAnalyticsDTO();
            // These calls must compile — they prove the fields exist
            assertThat(dto.getMarketContext()).isNull();
            assertThat(dto.getVolatilityContext()).isNull();
            assertThat(dto.getDealerPositioning()).isNull();
            assertThat(dto.getMarketStructure()).isNull();
            assertThat(dto.getHistoricalContext()).isNull();
            assertThat(dto.getExecutionLayer()).isNull();
            assertThat(dto.getExecutionTiming()).isNull();
            assertThat(dto.getConfidence()).isNull();
            assertThat(dto.getAutoTradeDecision()).isNull();
            assertThat(dto.getCompleteDecision()).isNull();
            assertThat(dto.getTradeSignal()).isNull();
            assertThat(dto.getRiskManagement()).isNull();
        }
    }

    // ── MarketContextDTO ──────────────────────────────────────────────────────

    @Nested @DisplayName("MarketContextDTO — verified fields")
    class MarketContextDTOTest {

        @Test @DisplayName("symbol, spot, atm, session exist — regime does NOT")
        void verifiedFields() {
            MarketContextDTO dto = MarketContextDTO.builder()
                    .symbol("NIFTY")
                    .spot(23114.5)
                    .atm(23100)
                    .build();
            assertThat(dto.getSymbol()).isEqualTo("NIFTY");
            assertThat(dto.getSpot()).isEqualTo(23114.5);
            assertThat(dto.getAtm()).isEqualTo(23100);
            assertThat(dto.getSession()).isNull();
            // NOTE: MarketContextDTO has NO getRegime() method
            // Regime is in HistoricalContextDTO
        }
    }

    // ── SessionDTO ────────────────────────────────────────────────────────────

    @Nested @DisplayName("SessionDTO — field is 'session', NOT 'sessionPhase'")
    class SessionDTOTest {

        @Test @DisplayName("getSession() returns session string — NOT getSessionPhase()")
        void sessionField() {
            SessionDTO s = SessionDTO.builder()
                    .session("MIDDAY")
                    .isMarket(true)
                    .build();
            // ✅ CORRECT field name
            assertThat(s.getSession()).isEqualTo("MIDDAY");
            assertThat(s.getIsMarket()).isTrue();
            // NOTE: SessionDTO has NO getSessionPhase() method
        }
    }

    // ── HistoricalContextDTO — where regime actually lives ────────────────────

    @Nested @DisplayName("HistoricalContextDTO — regime is here, NOT in MarketContextDTO")
    class HistoricalContextDTOTest {

        @Test @DisplayName("getRegime() exists on HistoricalContextDTO")
        void regimeOnHistoricalContext() {
            HistoricalContextDTO dto = HistoricalContextDTO.builder()
                    .symbol("NIFTY")
                    .regime("BULLISH")
                    .avgPcr(0.92)
                    .samples(100)
                    .build();
            assertThat(dto.getRegime()).isEqualTo("BULLISH");
            assertThat(dto.getSymbol()).isEqualTo("NIFTY");
        }
    }

    // ── VolatilityContextDTO ──────────────────────────────────────────────────

    @Nested @DisplayName("VolatilityContextDTO — NO ivRegime field")
    class VolatilityContextDTOTest {

        @Test @DisplayName("Fields are volatilityEngine and pcr only")
        void fields() {
            VolatilityContextDTO dto = new VolatilityContextDTO();
            assertThat(dto.getVolatilityEngine()).isNull();
            assertThat(dto.getPcr()).isNull();
            // NOTE: NO getIvRegime() method exists on VolatilityContextDTO
            // IV regime is at: dto.getDealerPositioning().getDealerInventoryModel().getVolatilityRegime()
        }
    }

    // ── PcrDTO — field name is pcr, NOT pcrValue ──────────────────────────────

    @Nested @DisplayName("PcrDTO — field is 'pcr', NOT 'pcrValue'")
    class PcrDTOTest {

        @Test @DisplayName("getPcr() is the correct method — NOT getPcrValue()")
        void pcrField() {
            PcrDTO pcr = PcrDTO.builder()
                    .pcr(0.92)
                    .sentiment("NEUTRAL")
                    .build();
            // ✅ CORRECT field name
            assertThat(pcr.getPcr()).isEqualTo(0.92);
            assertThat(pcr.getSentiment()).isEqualTo("NEUTRAL");
            // NOTE: PcrDTO has NO getPcrValue() method
        }
    }

    // ── DealerInventoryModelDTO — volatilityRegime + gammaFlip (Double) ───────

    @Nested @DisplayName("DealerInventoryModelDTO — volatilityRegime + gammaFlip as Double")
    class DealerInventoryModelDTOTest {

        @Test @DisplayName("volatilityRegime is the IV regime field")
        void volatilityRegime() {
            DealerInventoryModelDTO dto = DealerInventoryModelDTO.builder()
                    .volatilityRegime("HIGH_IV")
                    .dealerInventory("SHORT")
                    .build();
            assertThat(dto.getVolatilityRegime()).isEqualTo("HIGH_IV");
        }

        @Test @DisplayName("gammaFlip is Double (flip price level), NOT Boolean")
        void gammaFlipIsDouble() {
            DealerInventoryModelDTO dto = DealerInventoryModelDTO.builder()
                    .gammaFlip(23150.0)   // flip price level
                    .netGamma(-1200.0)
                    .build();
            // getGammaFlip() returns Double
            assertThat(dto.getGammaFlip()).isEqualTo(23150.0);
            assertThat(dto.getNetGamma()).isEqualTo(-1200.0);
            // Correct usage: dto.getGammaFlip() != null (not Boolean.TRUE.equals)
            assertThat(dto.getGammaFlip()).isNotNull();
        }

        @Test @DisplayName("Gamma flip absent → getGammaFlip() returns null")
        void gammaFlipAbsent() {
            DealerInventoryModelDTO dto = new DealerInventoryModelDTO();
            assertThat(dto.getGammaFlip()).isNull();
        }
    }

    // ── GammaDTO ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("GammaDTO — netGamma field verified")
    class GammaDTOTest {

        @Test @DisplayName("getNetGamma() exists, positive/negative cases")
        void netGamma() {
            GammaDTO pos = GammaDTO.builder().netGamma(3000.0).build();
            GammaDTO neg = GammaDTO.builder().netGamma(-1200.0).build();
            assertThat(pos.getNetGamma()).isGreaterThan(0);
            assertThat(neg.getNetGamma()).isLessThan(0);
        }
    }

    // ── CompressionDTO ────────────────────────────────────────────────────────

    @Nested @DisplayName("CompressionDTO — compressionDetected is Boolean")
    class CompressionDTOTest {

        @Test @DisplayName("compressionDetected correctly used as Boolean")
        void compressionDetected() {
            CompressionDTO dto = CompressionDTO.builder()
                    .compressionDetected(true)
                    .wallSpreadPct(1.2)
                    .build();
            assertThat(Boolean.TRUE.equals(dto.getCompressionDetected())).isTrue();
        }
    }

    // ── AutoTradeActionDTO ────────────────────────────────────────────────────

    @Nested @DisplayName("AutoTradeActionDTO — all execution fields")
    class AutoTradeActionDTOTest {

        @Test @DisplayName("action, direction, option, reason all exist")
        void fields() {
            AutoTradeActionDTO dto = AutoTradeActionDTO.builder()
                    .action("EXECUTE")
                    .direction("BUY")
                    .option("NIFTY24APR23200CE")
                    .reason("Gamma flip + high confidence")
                    .strategy("BREAKOUT")
                    .build();
            assertThat(dto.getAction()).isEqualTo("EXECUTE");
            assertThat(dto.getDirection()).isEqualTo("BUY");
            assertThat(dto.getOption()).isEqualTo("NIFTY24APR23200CE");
            assertThat(dto.getReason()).isEqualTo("Gamma flip + high confidence");
            // NOTE: AutoTradeActionDTO.reason is used for decisionReason in journal
            // NOT TradingCardDTO.rationale (which doesn't exist)
        }
    }

    // ── TradeSignalDTO ────────────────────────────────────────────────────────

    @Nested @DisplayName("TradeSignalDTO — field is 'strategy', NOT 'tradeType'")
    class TradeSignalDTOTest {

        @Test @DisplayName("getStrategy() exists — NOT getTradeType()")
        void strategyField() {
            TradeSignalDTO dto = TradeSignalDTO.builder()
                    .strategy("MOMENTUM")
                    .reason("PCR bullish")
                    .confidence("HIGH")
                    .build();
            // ✅ CORRECT
            assertThat(dto.getStrategy()).isEqualTo("MOMENTUM");
            // NOTE: TradeSignalDTO has NO getTradeType() method
        }
    }

    // ── FearIndexAnalysisDTO ──────────────────────────────────────────────────

    @Nested @DisplayName("FearIndexAnalysisDTO — currentFearIndex and zone")
    class FearIndexAnalysisDTOTest {

        @Test @DisplayName("getCurrentFearIndex() and getZone() verified")
        void fields() {
            FearIndexAnalysisDTO dto = FearIndexAnalysisDTO.builder()
                    .currentFearIndex(42.5)
                    .zone("NEUTRAL")
                    .recommendedAction("HOLD")
                    .build();
            assertThat(dto.getCurrentFearIndex()).isEqualTo(42.5);
            assertThat(dto.getZone()).isEqualTo("NEUTRAL");
        }
    }

    // ── ConfidenceDTO ─────────────────────────────────────────────────────────

    @Nested @DisplayName("ConfidenceDTO — confidenceScore field")
    class ConfidenceDTOTest {

        @Test @DisplayName("getConfidenceScore() returns Integer")
        void confidenceScore() {
            ConfidenceDTO dto = ConfidenceDTO.builder()
                    .confidenceScore(80)
                    .confidenceLevel("HIGH")
                    .build();
            assertThat(dto.getConfidenceScore()).isEqualTo(80);
        }
    }

    // ── ExecutionLayerDTO / FinalExecutionDTO ─────────────────────────────────

    @Nested @DisplayName("ExecutionLayerDTO + FinalExecutionDTO")
    class ExecutionLayerTest {

        @Test @DisplayName("executionReady and reason fields verified")
        void executionReady() {
            FinalExecutionDTO fe = FinalExecutionDTO.builder()
                    .executionReady(true)
                    .reason(null)
                    .build();
            assertThat(fe.getExecutionReady()).isTrue();
            assertThat(fe.getReason()).isNull();

            ExecutionLayerDTO el = ExecutionLayerDTO.builder()
                    .finalExecution(fe)
                    .build();
            assertThat(el.getFinalExecution().getExecutionReady()).isTrue();
        }
    }

    // ── ExecutionTimingDTO ────────────────────────────────────────────────────

    @Nested @DisplayName("ExecutionTimingDTO — entrySignal field")
    class ExecutionTimingDTOTest {

        @Test @DisplayName("getEntrySignal() exists")
        void entrySignal() {
            ExecutionTimingDTO dto = ExecutionTimingDTO.builder()
                    .entrySignal("BREAKOUT")
                    .entryType("MARKET")
                    .build();
            assertThat(dto.getEntrySignal()).isEqualTo("BREAKOUT");
        }
    }

    // ── RawOptionChainRecord ──────────────────────────────────────────────────

    @Nested @DisplayName("RawOptionChainRecord — §1 contract fields")
    class RawOptionChainRecordTest {

        @Test @DisplayName("All §1 fields buildable and accessible")
        void build() {
            RawOptionChainRecord r = RawOptionChainRecord.builder()
                    .symbol("NIFTY").expiry(LocalDate.now()).strike(23200)
                    .callOI(52731).callOIChange(1200).callVolume(2926468)
                    .callIV(22.28).callLTP(186.6)
                    .putOI(39504).putOIChange(980).putVolume(4039308)
                    .putIV(23.82).putLTP(261.55)
                    .spot(23114.5).futures(23140).timestamp(System.currentTimeMillis())
                    .build();
            assertThat(r.getSymbol()).isEqualTo("NIFTY");
            assertThat(r.getCallLTP()).isEqualTo(186.6);
            assertThat(r.getSpot()).isEqualTo(23114.5);
        }
    }

    // ── StrikeData ────────────────────────────────────────────────────────────

    @Nested @DisplayName("StrikeData — §2 derived fields + factory method")
    class StrikeDataTest {

        @Test @DisplayName("from() correctly computes oiImbalance, volumeImbalance, ivSkew")
        void derivedFields() {
            RawOptionChainRecord raw = RawOptionChainRecord.builder()
                    .symbol("NIFTY").expiry(LocalDate.now()).strike(23200)
                    .callOI(60000).callOIChange(0).callVolume(100000).callIV(22.0).callLTP(186.0)
                    .putOI(40000).putOIChange(0).putVolume(60000).putIV(21.0).putLTP(150.0)
                    .spot(23100).futures(23120).timestamp(System.currentTimeMillis())
                    .build();
            StrikeData sd = StrikeData.from(raw);
            assertThat(sd.getOiImbalance()).isEqualTo(0.2);     // (60000-40000)/100000
            assertThat(sd.getVolumeImbalance()).isEqualTo(0.25); // (100000-60000)/160000
            assertThat(sd.getIvSkew()).isEqualTo(1.0);           // 22 - 21
        }

        @Test @DisplayName("Zero OI does not throw ArithmeticException")
        void zeroOiSafe() {
            RawOptionChainRecord raw = RawOptionChainRecord.builder()
                    .symbol("NIFTY").expiry(LocalDate.now()).strike(23200)
                    .callOI(0).callOIChange(0).callVolume(0).callIV(0).callLTP(0)
                    .putOI(0).putOIChange(0).putVolume(0).putIV(0).putLTP(0)
                    .spot(23100).futures(23120).timestamp(System.currentTimeMillis())
                    .build();
            StrikeData sd = StrikeData.from(raw);
            assertThat(sd.getOiImbalance()).isEqualTo(0.0);
            assertThat(sd.getVolumeImbalance()).isEqualTo(0.0);
        }
    }

    // ── TradeDecision ─────────────────────────────────────────────────────────

    @Nested @DisplayName("TradeDecision — all §4 fields")
    class TradeDecisionTest {

        @Test @DisplayName("All contract fields buildable")
        void build() {
            TradeDecision td = TradeDecision.builder()
                    .symbol("NIFTY").strategy("MOMENTUM")
                    .strike("NIFTY24APR23200CE").expiry(LocalDate.now())
                    .entryType("MARKET").executionTiming("IMMEDIATE")
                    .stopLoss(130.0).target(280.0).confidence(78)
                    .lotSize(75).mode("PAPER")
                    .build();
            assertThat(td.getSymbol()).isEqualTo("NIFTY");
            assertThat(td.getStrategy()).isEqualTo("MOMENTUM");
            assertThat(td.getConfidence()).isEqualTo(78);
        }
    }

    // ── TradeState (Position contract) ────────────────────────────────────────

    @Nested @DisplayName("TradeState — §4 Position contract fields")
    class TradeStateTest {

        @Test @DisplayName("All contract fields settable and gettable")
        void contractFields() {
            TradeState s = new TradeState();
            s.setSymbol("NIFTY");
            s.setEntryPrice(200.0);
            s.setCurrentPrice(220.0);
            s.setStopLoss(160.0);
            s.setTarget(320.0);
            s.setTrailingStop(170.0);
            s.setQuantity(75);
            s.setDirection("BUY");
            s.setTradeActive(true);

            assertThat(s.getSymbol()).isEqualTo("NIFTY");
            assertThat(s.getEntryPrice()).isEqualTo(200.0);
            assertThat(s.getStopLoss()).isEqualTo(160.0);
            assertThat(s.getTrailingStop()).isEqualTo(170.0);
        }

        @Test @DisplayName("updatePnl() computes BUY PnL correctly")
        void pnlBuy() {
            TradeState s = new TradeState();
            s.setEntryPrice(200.0); s.setCurrentPrice(220.0);
            s.setQuantity(75); s.setDirection("BUY");
            s.updatePnl();
            assertThat(s.getPnl()).isEqualTo(1500.0); // 20 × 75
        }

        @Test @DisplayName("updatePnl() computes SELL PnL correctly")
        void pnlSell() {
            TradeState s = new TradeState();
            s.setEntryPrice(200.0); s.setCurrentPrice(220.0);
            s.setQuantity(75); s.setDirection("SELL");
            s.updatePnl();
            assertThat(s.getPnl()).isEqualTo(-1500.0); // adverse for SELL
        }

        @Test @DisplayName("updateRR() computes risk:reward correctly")
        void rr() {
            TradeState s = new TradeState();
            s.setEntryPrice(200.0); s.setStopLoss(160.0); // risk = 40
            s.setTarget(320.0); s.setCurrentPrice(240.0); // gained = 40
            s.updateRR();
            assertThat(s.getRrAchieved()).isEqualTo(1.0);
        }

        @Test @DisplayName("getSl() alias returns stopLoss")
        void slAlias() {
            TradeState s = new TradeState();
            s.setStopLoss(150.0);
            assertThat(s.getSl()).isEqualTo(150.0);
        }
    }
}

package com.pietrader.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.dto.decision.AutoTradeActionDTO;
import com.pietrader.dto.decision.CompleteDecisionDTO;
import com.pietrader.dto.decision.FearIndexAnalysisDTO;
import com.pietrader.dto.decision.FearIndexComponentsDTO;
import com.pietrader.dto.decision.TradeRecommendationsDTO;
import com.pietrader.dto.decision.TradingCardDTO;
import com.pietrader.dto.dealer.DealerInventoryModelDTO;
import com.pietrader.dto.dealer.DealerPositioningDTO;
import com.pietrader.dto.dealer.GammaDTO;
import com.pietrader.dto.dealer.GexDTO;
import com.pietrader.dto.execution.ExecutionDebugDTO;
import com.pietrader.dto.execution.ExecutionLayerDTO;
import com.pietrader.dto.execution.ExecutionTimingDTO;
import com.pietrader.dto.liquidity.LiquidityMapDTO;
import com.pietrader.dto.liquidity.LiquiditySweepDTO;
import com.pietrader.dto.liquidity.SupportResistanceDTO;
import com.pietrader.dto.market.CompressionDTO;
import com.pietrader.dto.market.ExpiryDTO;
import com.pietrader.dto.market.MarketContextDTO;
import com.pietrader.dto.market.MarketStructureDTO;
import com.pietrader.dto.market.PressureDTO;
import com.pietrader.dto.signal.TradeSignalDTO;
import com.pietrader.dto.volatility.PcrDTO;
import com.pietrader.dto.volatility.VolatilityContextDTO;
import com.pietrader.dto.volatility.VolatilityEngineDTO;
import com.pietrader.entity.TradeSignalEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeSignalMapper {

    private final ObjectMapper objectMapper;

    public TradeSignalEntity toEntity(OptionAnalyticsDTO dto) {
        if (dto == null) {
            log.warn("Received null OptionAnalyticsDTO — skipping mapping");
            return null;
        }

        TradeSignalEntity entity = new TradeSignalEntity();

        // ── MARKET CONTEXT ───────────────────────────────────────────────────
        MarketContextDTO ctx = dto.getMarketContext();
        if (ctx != null) {
            entity.setSymbol(ctx.getSymbol());
            entity.setSpot(ctx.getSpot());
            entity.setAtm(ctx.getAtm());

            ExpiryDTO expiry = ctx.getExpiry();
            if (expiry != null) {
                entity.setExpiry(expiry.getNearestExpiry());
                entity.setDaysToExpiry(expiry.getDaysToExpiry());
                entity.setPhase(expiry.getPhase());
            }
        }

        // ── TRADE SIGNAL ─────────────────────────────────────────────────────
        TradeSignalDTO signal = dto.getTradeSignal();
        if (signal != null) {
            entity.setStrategy(signal.getStrategy());
            entity.setSignalReason(signal.getReason());
            entity.setSignalConfidence(signal.getConfidence());
        }

        // ── AUTO TRADE DECISION ──────────────────────────────────────────────
        if (dto.getAutoTradeDecision() != null) {
            AutoTradeActionDTO action = dto.getAutoTradeDecision().getAutoTradeAction();
            if (action != null) {
                entity.setAction(action.getAction());
                entity.setDirection(action.getDirection());
                entity.setOptionStrike(action.getOption());
                entity.setDecisionConfidence(action.getConfidence());
                entity.setDecisionReason(action.getReason());
            }
            TradeRecommendationsDTO rec = dto.getAutoTradeDecision().getTradeRecommendations();
            if (rec != null) {
                entity.setRecommendationScore(rec.getScore());
                entity.setRecommendationLevel(rec.getConfidenceLevel());
            }
        }

        // ── MARKET STRUCTURE ─────────────────────────────────────────────────
        MarketStructureDTO structure = dto.getMarketStructure();
        if (structure != null) {
            CompressionDTO compression = structure.getCompression();
            if (compression != null) {
                entity.setCompressionDetected(compression.getCompressionDetected());
                entity.setBreakoutSignal(compression.getBreakoutSignal());
            }
            PressureDTO pressure = structure.getPressure();
            if (pressure != null) {
                entity.setPressureLabel(pressure.getLabel());
            }
        }

        // ── VOLATILITY ───────────────────────────────────────────────────────
        VolatilityContextDTO volatility = dto.getVolatilityContext();
        if (volatility != null) {
            VolatilityEngineDTO engine = volatility.getVolatilityEngine();
            if (engine != null) {
                entity.setAtmIvPct(engine.getAtmIvPct());
                entity.setDailyMove(engine.getDailyMove());
                entity.setWeeklyMove(engine.getWeeklyMove());
                entity.setUpper1d(engine.getUpper1d());
                entity.setLower1d(engine.getLower1d());
            }
            PcrDTO pcr = volatility.getPcr();
            if (pcr != null) {
                entity.setPcr(pcr.getPcr());
                entity.setPcrSentiment(pcr.getSentiment());
            }
        }

        // ── DEALER / GAMMA ───────────────────────────────────────────────────
        DealerPositioningDTO dealer = dto.getDealerPositioning();
        if (dealer != null) {
            DealerInventoryModelDTO inventory = dealer.getDealerInventoryModel();
            if (inventory != null) {
                entity.setDealerInventory(inventory.getDealerInventory());
                entity.setHedgingBehavior(inventory.getHedgingBehavior());
                entity.setGammaFlip(inventory.getGammaFlip());
                entity.setNetGamma(inventory.getNetGamma());
            }
            GexDTO gex = dealer.getGex();
            if (gex != null) {
                entity.setGammaRegime(gex.getGammaRegime());
            }
            GammaDTO gamma = dealer.getGamma();
            if (gamma != null) {
                entity.setCallGammaWall(gamma.getCallGammaWall());
                entity.setPutGammaWall(gamma.getPutGammaWall());
            }
        }

        // ── LIQUIDITY ────────────────────────────────────────────────────────
        LiquidityMapDTO liquidity = dto.getLiquidityMap();
        if (liquidity != null) {
            SupportResistanceDTO sr = liquidity.getSupportResistance();
            if (sr != null) {
                entity.setSupport(sr.getSupport());
                entity.setResistance(sr.getResistance());
            }
            LiquiditySweepDTO sweep = liquidity.getLiquiditySweep();
            if (sweep != null) {
                entity.setCallWall(sweep.getCallWall());
                entity.setPutWall(sweep.getPutWall());
                entity.setLiquiditySweepSignal(sweep.getSignal());
            }
        }

        // ── FEAR INDEX + TRADING CARD ─────────────────────────────────────────
        CompleteDecisionDTO decision = dto.getCompleteDecision();
        if (decision != null) {
            entity.setSignalTimestamp(decision.getTimestamp());

            FearIndexAnalysisDTO fear = decision.getFearIndexAnalysis();
            if (fear != null) {
                entity.setFearIndex(fear.getCurrentFearIndex());
                entity.setFearZone(fear.getZone());
                entity.setRecommendedAction(fear.getRecommendedAction());

                FearIndexComponentsDTO comp = fear.getComponents();
                if (comp != null) {
                    entity.setFearPcr(comp.getPcr()    != null ? comp.getPcr().getValue()    : null);
                    entity.setFearIv(comp.getIv()      != null ? comp.getIv().getValue()     : null);
                    entity.setFearGamma(comp.getGamma()!= null ? comp.getGamma().getValue()  : null);
                    entity.setFearVolume(comp.getVolume()!=null? comp.getVolume().getValue() : null);
                    entity.setFearSkew(comp.getSkew()  != null ? comp.getSkew().getValue()   : null);
                }
            }

            TradingCardDTO card = decision.getTradingCard();
            if (card != null) {
                entity.setSession1Card(card.getSession1());
                entity.setSession3Card(card.getSession3());
                entity.setBtst(card.getBtst());
            }
        }

        // ── EXECUTION ────────────────────────────────────────────────────────
        ExecutionLayerDTO layer = dto.getExecutionLayer();
        if (layer != null) {
            if (layer.getFinalExecution() != null) {
                entity.setExecutionReady(layer.getFinalExecution().getExecutionReady());
            }
            if (layer.getBreakout() != null) {
                entity.setBreakoutStatus(layer.getBreakout().getStatus());
                entity.setBreakoutTriggerPrice(layer.getBreakout().getTriggerPrice());
            }
        }

        ExecutionTimingDTO timing = dto.getExecutionTiming();
        if (timing != null) {
            entity.setEntrySignal(timing.getEntrySignal());
            entity.setEntryType(timing.getEntryType());
        }

        ExecutionDebugDTO debug = dto.getExecutionDebug();
        if (debug != null) {
            if (debug.getTradingWindow() != null) {
                entity.setTradingWindow(debug.getTradingWindow().getWindow());
            }
            if (debug.getFakeBreakout() != null) {
                entity.setIsFakeBreakout(debug.getFakeBreakout().getFakeBreakout());
            }
        }

        // ── RAW JSON (audit / replay) ─────────────────────────────────────────
        try {
            entity.setRawPayload(objectMapper.writeValueAsString(dto));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize raw payload for symbol={}: {}",
                entity.getSymbol(), e.getMessage());
        }

        return entity;
    }
}

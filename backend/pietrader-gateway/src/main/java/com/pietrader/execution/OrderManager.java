package com.pietrader.execution;

import com.pietrader.broker.model.OrderResponse;
import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.execution.model.Trade;
import com.pietrader.execution.model.TradeMode;
import com.pietrader.service.BrokerOrderService;
import com.pietrader.service.PaperTradeService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * PIE TRADER — OrderManager
 * Delegates to BrokerOrderService (live) or PaperTradeService (paper).
 *
 * DTO field usage — all verified:
 *  - regime:   HistoricalContextDTO.regime  (MarketContextDTO has NO regime)
 *  - strategy: TradeSignalDTO.strategy      (TradeSignalDTO has NO tradeType)
 *  - spot:     MarketContextDTO.spot        (used as entry price proxy for paper)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderManager {

    private final BrokerOrderService brokerOrderService;
    private final PaperTradeService  paperTradeService;
    private final ModeManager        modeManager;

    @Value("${trading.lot.size:75}")        private int    lotSize;
    @Value("${trading.sl.percent:30.0}")    private double slPercent;
    @Value("${trading.target.percent:60.0}") private double targetPercent;

    public Trade execute(TradeRequest request) {
        log.info("📋 OrderManager.execute → {} {} {} lots={} mode={}",
            request.getSymbol(), request.getStrike(), request.getDirection(),
            request.getLots(), request.getMode());

        int quantity = request.getLots() * lotSize;

        OrderResponse resp = request.getMode() == TradeMode.PAPER
            ? paperTradeService.placeOrder(request.getSymbol(), request.getStrike(), request.getDirection(), quantity)
            : brokerOrderService.placeOrder(request.getSymbol(), request.getStrike(), request.getDirection(), quantity);

        return buildTrade(request, quantity, resp);
    }

    public Trade forceExecute(String symbol, String strike, String direction) {
        return execute(TradeRequest.builder()
            .tradeId("FORCE_" + System.currentTimeMillis())
            .symbol(symbol).strike(strike).direction(direction)
            .lots(1).confidence(0).mode(modeManager.currentMode())
            .requestedAt(Instant.now().toEpochMilli()).build());
    }

    private Trade buildTrade(TradeRequest req, int quantity, OrderResponse resp) {
        if (!"SUCCESS".equals(resp.getStatus()) && !"PAPER".equals(resp.getStatus())) {
            log.error("❌ Order rejected → {}", resp.getErrorMessage());
            return Trade.builder()
                .tradeId(req.getTradeId()).symbol(req.getSymbol())
                .strike(req.getStrike()).direction(req.getDirection())
                .lots(req.getLots()).quantity(quantity)
                .mode(req.getMode()).confidence(req.getConfidence())
                .entryTime(System.currentTimeMillis())
                .success(false).failureReason(resp.getErrorMessage()).build();
        }

        double entry  = resp.getPrice() > 0 ? resp.getPrice() : estimateEntry(req.getDto());
        double sl     = Math.max(1.0, entry * (1.0 - slPercent / 100.0));
        double target = entry * (1.0 + targetPercent / 100.0);

        log.info("✅ Order placed → orderId={} entry={} sl={} target={}",
            resp.getOrderId(), entry, sl, target);

        return Trade.builder()
            .tradeId(req.getTradeId()).orderId(resp.getOrderId())
            .symbol(req.getSymbol()).strike(req.getStrike()).direction(req.getDirection())
            .lots(req.getLots()).quantity(quantity)
            .entryPrice(entry).sl(sl).target(target)
            .entryTime(System.currentTimeMillis())
            .mode(req.getMode()).confidence(req.getConfidence())
            .regime(resolveRegime(req.getDto()))
            .tradeType("INTRADAY")
            .strategy(resolveStrategy(req.getDto()))
            .success(true).build();
    }

    /** MarketContextDTO.spot — verified field */
    private double estimateEntry(OptionAnalyticsDTO dto) {
        try {
            if (dto != null && dto.getMarketContext() != null) {
                Double spot = dto.getMarketContext().getSpot();
                if (spot != null && spot > 0) return spot;
            }
        } catch (Exception ignored) {}
        return 0.0;
    }

    /** HistoricalContextDTO.regime — MarketContextDTO has NO regime field */
    private String resolveRegime(OptionAnalyticsDTO dto) {
        try {
            if (dto != null && dto.getHistoricalContext() != null) {
                String r = dto.getHistoricalContext().getRegime();
                if (r != null) return r;
            }
        } catch (Exception ignored) {}
        return "UNKNOWN";
    }

    /** TradeSignalDTO.strategy — TradeSignalDTO has NO tradeType field */
    private String resolveStrategy(OptionAnalyticsDTO dto) {
        try {
            if (dto != null && dto.getTradeSignal() != null) {
                String s = dto.getTradeSignal().getStrategy();
                if (s != null) return s;
            }
        } catch (Exception ignored) {}
        return "MOMENTUM";
    }

    @Getter @Builder
    public static class TradeRequest {
        private String             tradeId;
        private String             symbol;
        private String             strike;
        private String             direction;
        private int                lots;
        private int                confidence;
        private TradeMode          mode;
        private OptionAnalyticsDTO dto;
        private long               requestedAt;
    }
}

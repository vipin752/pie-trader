package com.pietrader.controller;

import com.pietrader.dto.OptionAnalyticsDTO;
import com.pietrader.entity.TradeSignalEntity;
import com.pietrader.service.OptionService;
import com.pietrader.service.TradeSignalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Trade Signal REST API
 */
@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
@Tag(name = "Signals", description = "Trade signal endpoints")
public class TradeSingnalController {

    private final TradeSignalService tradeSignalService;
    private final OptionService      optionService;

    @GetMapping("/latest/{symbol}")
    @Operation(summary = "Get latest signal for symbol")
    public ResponseEntity<TradeSignalEntity> getLatest(@PathVariable String symbol) {
        return tradeSignalService.getLatest(symbol)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/history/{symbol}")
    @Operation(summary = "Get signal history for symbol")
    public ResponseEntity<List<TradeSignalEntity>> getHistory(@PathVariable String symbol) {
        return ResponseEntity.ok(tradeSignalService.getHistory(symbol));
    }

    @GetMapping("/live/{symbol}")
    @Operation(summary = "Fetch live analytics from Python engine")
    public ResponseEntity<String> getLive(@PathVariable String symbol) {
        String result = optionService.getOptionSummary(symbol);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/cached/{symbol}")
    @Operation(summary = "Get cached (Redis) signal for symbol")
    public ResponseEntity<OptionAnalyticsDTO> getCached(@PathVariable String symbol) {
        OptionAnalyticsDTO dto = optionService.getLatestSignal(symbol);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.noContent().build();
    }

    @GetMapping("/summary/{symbol}")
    @Operation(summary = "Get signal summary for dashboard")
    public ResponseEntity<Map<String, Object>> getSummary(@PathVariable String symbol) {
        return tradeSignalService.getLatest(symbol)
            .map(e -> ResponseEntity.ok(Map.<String, Object>of(
                "symbol",     e.getSymbol(),
                "spot",       e.getSpot(),
                "strategy",   e.getStrategy(),
                "action",     e.getAction(),
                "direction",  e.getDirection(),
                "strike",     e.getOptionStrike(),
                "confidence", e.getDecisionConfidence(),
                "fearIndex",  e.getFearIndex(),
                "fearZone",   e.getFearZone(),
                "sessionCard", e.getSession1Card()
            )))
            .orElse(ResponseEntity.noContent().build());
    }
}

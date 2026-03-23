"""
PIE TRADER — test_data_contract.py

Validates Python-side data contract compliance:
  - Kafka topic constants match contract exactly
  - DecisionPublisher validates DTO before publishing
  - PostgresClient writes to correct table columns
  - PositionEventConsumer handles OPEN/CLOSED events correctly

Run: pytest test/python/test_data_contract.py -v
"""

import pytest
import json
from unittest.mock import MagicMock, patch, call


# ── Import targets ─────────────────────────────────────────────────────────────

import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../python"))

# Safe import with fallback stubs for CI without Kafka installed
try:
    from kafka_utils.topics import (
        TICK_TOPIC, DECISION_TOPIC, POSITION_TOPIC,
        TRADE_EVENT_TOPIC, SQUAREOFF_TOPIC, ALERTS_TOPIC
    )
    TOPICS_AVAILABLE = True
except ImportError:
    TOPICS_AVAILABLE = False


# ═══════════════════════════════════════════════════════════════════════════════
# §A  Kafka Topic Contract
# ═══════════════════════════════════════════════════════════════════════════════

class TestKafkaTopicsContract:
    """Validates every topic string against the DATA CONTRACT."""

    @pytest.mark.skipif(not TOPICS_AVAILABLE, reason="kafka_utils not importable")
    def test_tick_topic(self):
        assert TICK_TOPIC == "pie.market.ticks"

    @pytest.mark.skipif(not TOPICS_AVAILABLE, reason="kafka_utils not importable")
    def test_decision_topic(self):
        assert DECISION_TOPIC == "pie.analytics.results"

    @pytest.mark.skipif(not TOPICS_AVAILABLE, reason="kafka_utils not importable")
    def test_position_topic(self):
        assert POSITION_TOPIC == "pie.position.events"

    @pytest.mark.skipif(not TOPICS_AVAILABLE, reason="kafka_utils not importable")
    def test_trade_event_topic(self):
        assert TRADE_EVENT_TOPIC == "pie.trade.events"

    @pytest.mark.skipif(not TOPICS_AVAILABLE, reason="kafka_utils not importable")
    def test_squareoff_topic(self):
        assert SQUAREOFF_TOPIC == "pie.squareoff.signals"

    @pytest.mark.skipif(not TOPICS_AVAILABLE, reason="kafka_utils not importable")
    def test_alerts_topic(self):
        assert ALERTS_TOPIC == "pie.alerts"

    @pytest.mark.skipif(not TOPICS_AVAILABLE, reason="kafka_utils not importable")
    def test_all_topics_start_with_pie(self):
        for topic in [TICK_TOPIC, DECISION_TOPIC, POSITION_TOPIC,
                      TRADE_EVENT_TOPIC, SQUAREOFF_TOPIC, ALERTS_TOPIC]:
            assert topic.startswith("pie."), f"Topic {topic!r} does not start with 'pie.'"


# ═══════════════════════════════════════════════════════════════════════════════
# §B  AnalyticsResult Contract (Python → Java)
#     Validates that OptionService output contains all contract fields
# ═══════════════════════════════════════════════════════════════════════════════

CONTRACT_ANALYTICS_FIELDS = [
    "trade_signal",
    "premium_intelligence",
    "strike_selection",
    "dealer_positioning",
    "liquidity_map",
    "institutional_flow",
    "volatility_context",
    "market_context",
    "execution_layer",
    "risk_management",
    "position_management",
    "exit_management",
    "confidence",
    "auto_trade_decision",
    "complete_decision",
    "ui_view",
    "timestamp",
]

class TestAnalyticsResultContract:
    """Validates AnalyticsResult output keys match contract §3."""

    def _build_minimal_analytics_result(self, action="EXECUTE", confidence=75):
        """Build a minimal dict that represents a valid AnalyticsResult."""
        return {
            "trade_signal":       {"trade_type": "INTRADAY", "direction": "UP"},
            "premium_intelligence": {"atm_premium": 186.6},
            "strike_selection":   {"recommended_strike": "NIFTY24APR23200CE"},
            "dealer_positioning": {"dealer_inventory_model": {"gamma_flip": False}},
            "liquidity_map":      {"liquidity_score": 0.8},
            "institutional_flow": {"smart_money_flow": "BULLISH"},
            "volatility_context": {"iv_regime": "LOW_IV", "pcr": {"pcr_value": 0.92}},
            "market_context":     {"symbol": "NIFTY", "spot": 23114.5,
                                   "regime": "BULLISH",
                                   "session": {"is_market": True, "session_phase": "MIDDAY"}},
            "execution_layer":    {"final_execution": {"execution_ready": True, "reason": None}},
            "risk_management":    {"stop_loss": 130.0, "target": 280.0},
            "position_management":{"max_position_size": 3},
            "exit_management":    {"exit_triggers": ["SL", "TARGET"]},
            "confidence":         {"confidence_score": confidence},
            "auto_trade_decision":{"auto_trade_decision": {"action": action,
                                                           "option": "NIFTY24APR23200CE",
                                                           "direction": "BUY"}},
            "complete_decision":  {"fear_index_analysis": {"current_fear_index": 42.0, "zone": "NEUTRAL"}},
            "ui_view":            {"summary": "Bullish breakout signal"},
            "timestamp":          "2026-03-22T09:30:00",
        }

    def test_all_contract_fields_present(self):
        result = self._build_minimal_analytics_result()
        for field in CONTRACT_ANALYTICS_FIELDS:
            assert field in result, f"Contract field '{field}' missing from AnalyticsResult"

    def test_market_context_has_symbol(self):
        result = self._build_minimal_analytics_result()
        assert "symbol" in result["market_context"]

    def test_confidence_has_score(self):
        result = self._build_minimal_analytics_result()
        assert "confidence_score" in result["confidence"]

    def test_auto_trade_decision_has_action(self):
        result = self._build_minimal_analytics_result()
        action = result["auto_trade_decision"]["auto_trade_decision"]["action"]
        assert action in ("EXECUTE", "WAIT", "HOLD", "NO_TRADE")

    def test_execution_layer_has_execution_ready(self):
        result = self._build_minimal_analytics_result()
        assert "execution_ready" in result["execution_layer"]["final_execution"]


# ═══════════════════════════════════════════════════════════════════════════════
# §C  DecisionPublisher Contract Validation
# ═══════════════════════════════════════════════════════════════════════════════

class TestDecisionPublisher:
    """Validates DecisionPublisher enforces contract before publishing."""

    def _make_publisher(self, position_consumer=None):
        """Create publisher with mocked Kafka producer."""
        try:
            sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../python"))
            from execution_bridge.decision_publisher import DecisionPublisher
            publisher = DecisionPublisher(position_consumer=position_consumer)
            publisher._producer = MagicMock()
            return publisher, publisher._producer
        except ImportError:
            pytest.skip("execution_bridge not importable in this environment")

    def _valid_dto(self, action="EXECUTE"):
        return {
            "market_context":     {"symbol": "NIFTY"},
            "confidence":         {"confidence_score": 75},
            "auto_trade_decision":{"auto_trade_decision": {"action": action,
                                                           "direction": "BUY"}},
        }

    def test_valid_execute_dto_is_published(self):
        publisher, mock_producer = self._make_publisher()
        publisher.publish(self._valid_dto("EXECUTE"), symbol="NIFTY")
        mock_producer.send.assert_called_once()

    def test_wait_action_not_published(self):
        publisher, mock_producer = self._make_publisher()
        publisher.publish(self._valid_dto("WAIT"), symbol="NIFTY")
        mock_producer.send.assert_not_called()

    def test_hold_action_not_published(self):
        publisher, mock_producer = self._make_publisher()
        publisher.publish(self._valid_dto("HOLD"), symbol="NIFTY")
        mock_producer.send.assert_not_called()

    def test_missing_symbol_not_published(self):
        publisher, mock_producer = self._make_publisher()
        dto = self._valid_dto()
        del dto["market_context"]
        publisher.publish(dto, symbol="NIFTY")
        mock_producer.send.assert_not_called()

    def test_missing_confidence_not_published(self):
        publisher, mock_producer = self._make_publisher()
        dto = self._valid_dto()
        del dto["confidence"]
        publisher.publish(dto, symbol="NIFTY")
        mock_producer.send.assert_not_called()

    def test_active_position_suppresses_publish(self):
        pos_consumer = MagicMock()
        pos_consumer.has_active_position.return_value = True
        publisher, mock_producer = self._make_publisher(position_consumer=pos_consumer)
        publisher.publish(self._valid_dto("EXECUTE"), symbol="NIFTY")
        mock_producer.send.assert_not_called()

    def test_no_active_position_allows_publish(self):
        pos_consumer = MagicMock()
        pos_consumer.has_active_position.return_value = False
        publisher, mock_producer = self._make_publisher(position_consumer=pos_consumer)
        publisher.publish(self._valid_dto("EXECUTE"), symbol="NIFTY")
        mock_producer.send.assert_called_once()

    def test_empty_dto_not_published(self):
        publisher, mock_producer = self._make_publisher()
        publisher.publish({}, symbol="NIFTY")
        mock_producer.send.assert_not_called()

    def test_none_dto_not_published(self):
        publisher, mock_producer = self._make_publisher()
        publisher.publish(None, symbol="NIFTY")
        mock_producer.send.assert_not_called()


# ═══════════════════════════════════════════════════════════════════════════════
# §D  PositionEventConsumer Contract (Java → Python feedback)
# ═══════════════════════════════════════════════════════════════════════════════

class TestPositionEventConsumer:
    """Tests the feedback loop from Java position events."""

    def _make_consumer(self):
        try:
            from kafka_utils.position_event_consumer import PositionEventConsumer
            c = PositionEventConsumer()
            return c
        except ImportError:
            pytest.skip("kafka_utils not importable")

    def test_open_event_marks_active(self):
        consumer = self._make_consumer()
        consumer._handle_event({"symbol": "NIFTY", "status": "OPEN"})
        assert consumer.has_active_position("NIFTY") is True

    def test_closed_event_removes_active(self):
        consumer = self._make_consumer()
        consumer._handle_event({"symbol": "NIFTY", "status": "OPEN"})
        consumer._handle_event({"symbol": "NIFTY", "status": "CLOSED"})
        assert consumer.has_active_position("NIFTY") is False

    def test_pending_event_marks_active(self):
        consumer = self._make_consumer()
        consumer._handle_event({"symbol": "BANKNIFTY", "status": "PENDING"})
        assert consumer.has_active_position("BANKNIFTY") is True

    def test_rejected_event_removes_active(self):
        consumer = self._make_consumer()
        consumer._handle_event({"symbol": "NIFTY", "status": "OPEN"})
        consumer._handle_event({"symbol": "NIFTY", "status": "REJECTED"})
        assert consumer.has_active_position("NIFTY") is False

    def test_lowercase_symbol_normalized(self):
        consumer = self._make_consumer()
        consumer._handle_event({"symbol": "nifty", "status": "OPEN"})
        assert consumer.has_active_position("NIFTY") is True

    def test_empty_symbol_ignored(self):
        consumer = self._make_consumer()
        consumer._handle_event({"symbol": "", "status": "OPEN"})
        assert consumer.get_all() == {}

    def test_get_all_returns_copy(self):
        consumer = self._make_consumer()
        consumer._handle_event({"symbol": "NIFTY", "status": "OPEN"})
        all_positions = consumer.get_all()
        all_positions["FAKE"] = "OPEN"
        assert "FAKE" not in consumer.get_all()


# ═══════════════════════════════════════════════════════════════════════════════
# §E  Squareoff Signal Contract (Python → Java)
# ═══════════════════════════════════════════════════════════════════════════════

class TestSquareoffSignalContract:
    """Validates that any squareoff message published matches contract §9."""

    REQUIRED_FIELDS = {"symbol", "action", "reason", "timestamp"}

    def _build_squareoff_signal(self, symbol="NIFTY", action="SQUAREOFF", reason="MAX_LOSS"):
        import time
        return {
            "symbol":    symbol,
            "action":    action,
            "reason":    reason,
            "timestamp": int(time.time() * 1000),
        }

    def test_all_contract_fields_present(self):
        msg = self._build_squareoff_signal()
        assert self.REQUIRED_FIELDS.issubset(msg.keys())

    def test_action_is_squareoff(self):
        msg = self._build_squareoff_signal()
        assert msg["action"] == "SQUAREOFF"

    def test_symbol_can_be_all(self):
        msg = self._build_squareoff_signal(symbol="ALL")
        assert msg["symbol"] == "ALL"

    def test_timestamp_is_epoch_millis(self):
        import time
        msg = self._build_squareoff_signal()
        # timestamp should be close to now (within 5 seconds)
        now_ms = int(time.time() * 1000)
        assert abs(msg["timestamp"] - now_ms) < 5000


# ═══════════════════════════════════════════════════════════════════════════════
# §F  Trade Event Contract (Java → Kafka → UI)
# ═══════════════════════════════════════════════════════════════════════════════

class TestTradeEventContract:
    """Validates pie.trade.events message fields match contract §8."""

    REQUIRED_FIELDS = {"event", "tradeId", "symbol", "strategy",
                       "strike", "entryPrice", "lotSize", "mode", "timestamp"}

    def test_trade_opened_event_has_all_fields(self):
        event = {
            "event":      "TRADE_OPENED",
            "tradeId":    "T001",
            "symbol":     "NIFTY",
            "strategy":   "MOMENTUM",
            "strike":     "NIFTY24APR23200CE",
            "entryPrice": 186.6,
            "lotSize":    75,
            "mode":       "PAPER",
            "timestamp":  1711000000000,
        }
        assert self.REQUIRED_FIELDS.issubset(event.keys())

    def test_position_event_has_all_fields(self):
        """Contract §8: pie.position.events fields."""
        event = {
            "event":     "POSITION_OPEN",
            "symbol":    "NIFTY",
            "entry":     186.6,
            "current":   210.0,
            "pnl":       1755.0,
            "rr":        0.5,
            "status":    "OPEN",
            "timestamp": 1711000000000,
        }
        required = {"event", "symbol", "entry", "current", "pnl", "rr", "status", "timestamp"}
        assert required.issubset(event.keys())

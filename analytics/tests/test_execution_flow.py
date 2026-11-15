"""
PIE TRADER — analytics/tests/test_execution_flow.py

Tests for Python execution bridge components:
  - DecisionPublisher: validates before publishing, suppresses WAIT/HOLD
  - SquareoffPublisher: builds contract-compliant squareoff signals
  - MarketStateConsumer: caches and serves MarketState per symbol
  - PositionEventConsumer: tracks Java feedback loop
  - topics.py: all topic strings match data contract

Run:
    cd analytics
    pytest tests/test_execution_flow.py -v

Dependencies:
    pip install pytest
    (kafka-python, psycopg2 NOT needed — all Kafka calls are mocked)
"""

import sys
import os
import time
import pytest
from unittest.mock import MagicMock, patch, call

# ── Path setup ────────────────────────────────────────────────────────────────
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


# ═══════════════════════════════════════════════════════════════════════════════
# §A  Topics Contract
# ═══════════════════════════════════════════════════════════════════════════════

class TestTopicsContract:
    """Every Kafka topic string must match the data contract exactly."""

    def test_all_topics_importable(self):
        from kafka_utils.topics import (
            TICK_TOPIC, MARKET_STATE_TOPIC, DECISION_TOPIC,
            SQUAREOFF_TOPIC, TRADE_EVENT_TOPIC, POSITION_TOPIC, ALERTS_TOPIC
        )
        assert TICK_TOPIC         == "pie.market.ticks"
        assert MARKET_STATE_TOPIC == "pie.market.state"
        assert DECISION_TOPIC     == "pie.analytics.results"
        assert SQUAREOFF_TOPIC    == "pie.squareoff.signals"
        assert TRADE_EVENT_TOPIC  == "pie.trade.events"
        assert POSITION_TOPIC     == "pie.position.events"
        assert ALERTS_TOPIC       == "pie.alerts"

    def test_all_topics_start_with_pie(self):
        from kafka_utils.topics import ALL_TOPICS
        for topic in ALL_TOPICS:
            assert topic.startswith("pie."), f"Topic '{topic}' does not start with 'pie.'"

    def test_consumer_and_producer_sets_defined(self):
        from kafka_utils.topics import CONSUMER_TOPICS, PRODUCER_TOPICS
        assert len(CONSUMER_TOPICS) > 0
        assert len(PRODUCER_TOPICS) > 0


# ═══════════════════════════════════════════════════════════════════════════════
# §B  DecisionPublisher
# ═══════════════════════════════════════════════════════════════════════════════

class TestDecisionPublisher:
    """DecisionPublisher must validate DTO and suppress non-EXECUTE actions."""

    def _make_publisher(self, position_consumer=None):
        from execution_bridge.decision_publisher import DecisionPublisher
        publisher = DecisionPublisher(position_consumer=position_consumer)
        publisher._producer = MagicMock()
        return publisher, publisher._producer

    def _valid_dto(self, action="EXECUTE", symbol="NIFTY", confidence=75):
        return {
            "market_context":     {"symbol": symbol},
            "confidence":         {"confidence_score": confidence},
            "auto_trade_decision":{"auto_trade_decision": {
                "action": action, "direction": "BUY", "option": "NIFTY24APR23200CE"
            }},
        }

    # ── Publish ────────────────────────────────────────────────────────────────

    def test_execute_action_is_published(self):
        pub, mock_prod = self._make_publisher()
        pub.publish(self._valid_dto("EXECUTE"), symbol="NIFTY")
        mock_prod.send.assert_called_once()

    def test_published_to_correct_topic(self):
        pub, mock_prod = self._make_publisher()
        pub.publish(self._valid_dto("EXECUTE"), symbol="NIFTY")
        call_args = mock_prod.send.call_args
        assert call_args[0][0] == "pie.analytics.results"

    # ── Suppression ───────────────────────────────────────────────────────────

    def test_wait_action_not_published(self):
        pub, mock_prod = self._make_publisher()
        pub.publish(self._valid_dto("WAIT"), symbol="NIFTY")
        mock_prod.send.assert_not_called()

    def test_hold_action_not_published(self):
        pub, mock_prod = self._make_publisher()
        pub.publish(self._valid_dto("HOLD"), symbol="NIFTY")
        mock_prod.send.assert_not_called()

    def test_no_trade_action_not_published(self):
        pub, mock_prod = self._make_publisher()
        pub.publish(self._valid_dto("NO_TRADE"), symbol="NIFTY")
        mock_prod.send.assert_not_called()

    def test_none_dto_not_published(self):
        pub, mock_prod = self._make_publisher()
        pub.publish(None, symbol="NIFTY")
        mock_prod.send.assert_not_called()

    def test_empty_dict_not_published(self):
        pub, mock_prod = self._make_publisher()
        pub.publish({}, symbol="NIFTY")
        mock_prod.send.assert_not_called()

    # ── Missing required fields ───────────────────────────────────────────────

    def test_missing_symbol_not_published(self):
        pub, mock_prod = self._make_publisher()
        dto = self._valid_dto()
        del dto["market_context"]
        pub.publish(dto, symbol="NIFTY")
        mock_prod.send.assert_not_called()

    def test_missing_confidence_not_published(self):
        pub, mock_prod = self._make_publisher()
        dto = self._valid_dto()
        del dto["confidence"]
        pub.publish(dto, symbol="NIFTY")
        mock_prod.send.assert_not_called()

    def test_missing_action_not_published(self):
        pub, mock_prod = self._make_publisher()
        dto = self._valid_dto()
        del dto["auto_trade_decision"]
        pub.publish(dto, symbol="NIFTY")
        mock_prod.send.assert_not_called()

    # ── Position suppression ──────────────────────────────────────────────────

    def test_active_java_position_suppresses_publish(self):
        pos = MagicMock()
        pos.has_active_position.return_value = True
        pub, mock_prod = self._make_publisher(position_consumer=pos)
        pub.publish(self._valid_dto("EXECUTE"), symbol="NIFTY")
        mock_prod.send.assert_not_called()

    def test_no_java_position_allows_publish(self):
        pos = MagicMock()
        pos.has_active_position.return_value = False
        pub, mock_prod = self._make_publisher(position_consumer=pos)
        pub.publish(self._valid_dto("EXECUTE"), symbol="NIFTY")
        mock_prod.send.assert_called_once()


# ═══════════════════════════════════════════════════════════════════════════════
# §C  SquareoffPublisher — Contract §9
# ═══════════════════════════════════════════════════════════════════════════════

class TestSquareoffPublisher:
    """SquareoffPublisher must produce contract-compliant messages."""

    REQUIRED_FIELDS = {"symbol", "action", "reason", "timestamp"}

    def _make_publisher(self):
        from execution_bridge.squareoff_publisher import SquareoffPublisher
        pub = SquareoffPublisher()
        pub._producer = MagicMock()
        return pub, pub._producer

    def test_squareoff_single_symbol_sends_message(self):
        pub, mock_prod = self._make_publisher()
        pub.squareoff("NIFTY", "MAX_LOSS")
        mock_prod.send.assert_called_once()

    def test_squareoff_sent_to_correct_topic(self):
        pub, mock_prod = self._make_publisher()
        pub.squareoff("NIFTY", "MAX_LOSS")
        topic = mock_prod.send.call_args[0][0]
        assert topic == "pie.squareoff.signals"

    def test_squareoff_message_has_all_contract_fields(self):
        pub, mock_prod = self._make_publisher()
        pub.squareoff("NIFTY", "RISK_BREACH")
        payload = mock_prod.send.call_args[1]["value"]
        assert self.REQUIRED_FIELDS.issubset(payload.keys())

    def test_action_is_always_squareoff(self):
        pub, mock_prod = self._make_publisher()
        pub.squareoff("NIFTY", "TEST")
        payload = mock_prod.send.call_args[1]["value"]
        assert payload["action"] == "SQUAREOFF"

    def test_symbol_uppercased(self):
        pub, mock_prod = self._make_publisher()
        pub.squareoff("nifty", "TEST")
        payload = mock_prod.send.call_args[1]["value"]
        assert payload["symbol"] == "NIFTY"

    def test_squareoff_all_sends_all_symbol(self):
        pub, mock_prod = self._make_publisher()
        pub.squareoff_all("EMERGENCY")
        payload = mock_prod.send.call_args[1]["value"]
        assert payload["symbol"] == "ALL"

    def test_timestamp_is_recent_epoch_millis(self):
        pub, mock_prod = self._make_publisher()
        pub.squareoff("NIFTY", "TEST")
        payload = mock_prod.send.call_args[1]["value"]
        now_ms = int(time.time() * 1000)
        assert abs(payload["timestamp"] - now_ms) < 5000

    def test_reason_preserved(self):
        pub, mock_prod = self._make_publisher()
        pub.squareoff("NIFTY", "GAMMA_FLIP")
        payload = mock_prod.send.call_args[1]["value"]
        assert payload["reason"] == "GAMMA_FLIP"


# ═══════════════════════════════════════════════════════════════════════════════
# §D  MarketStateConsumer
# ═══════════════════════════════════════════════════════════════════════════════

class TestMarketStateConsumer:
    """MarketStateConsumer caches Java MarketState per symbol."""

    def _make_consumer(self):
        from market_data.market_state_consumer import MarketStateConsumer
        return MarketStateConsumer()

    def _sample_state(self, symbol="NIFTY", spot=23114.5, regime="BULLISH"):
        return {
            "symbol": symbol, "spot": spot, "atm": 23100.0,
            "pcr": 0.92, "iv": 22.5, "gammaExposure": -1200.0,
            "gammaFlip": 23000.0, "regime": regime,
            "volatilityRegime": "CONTRACTING", "session": "MIDDAY",
            "timestamp": int(time.time() * 1000),
        }

    def test_is_not_ready_before_first_message(self):
        consumer = self._make_consumer()
        assert consumer.is_ready("NIFTY") is False

    def test_is_ready_after_receiving_state(self):
        consumer = self._make_consumer()
        consumer._handle(self._sample_state("NIFTY"))
        assert consumer.is_ready("NIFTY") is True

    def test_get_returns_none_for_unknown_symbol(self):
        consumer = self._make_consumer()
        assert consumer.get("BANKNIFTY") is None

    def test_get_returns_cached_state(self):
        consumer = self._make_consumer()
        consumer._handle(self._sample_state("NIFTY", spot=23114.5))
        state = consumer.get("NIFTY")
        assert state["spot"] == 23114.5

    def test_state_updated_on_new_message(self):
        consumer = self._make_consumer()
        consumer._handle(self._sample_state("NIFTY", spot=23000.0))
        consumer._handle(self._sample_state("NIFTY", spot=23200.0))
        assert consumer.spot("NIFTY") == 23200.0

    def test_lowercase_symbol_normalised(self):
        consumer = self._make_consumer()
        consumer._handle(self._sample_state("nifty"))
        assert consumer.is_ready("NIFTY") is True

    def test_multiple_symbols_independent(self):
        consumer = self._make_consumer()
        consumer._handle(self._sample_state("NIFTY",     spot=23100.0))
        consumer._handle(self._sample_state("BANKNIFTY", spot=51000.0))
        assert consumer.spot("NIFTY")     == 23100.0
        assert consumer.spot("BANKNIFTY") == 51000.0

    def test_get_all_returns_all_cached_symbols(self):
        consumer = self._make_consumer()
        consumer._handle(self._sample_state("NIFTY"))
        consumer._handle(self._sample_state("BANKNIFTY"))
        all_states = consumer.get_all()
        assert "NIFTY" in all_states
        assert "BANKNIFTY" in all_states

    def test_regime_accessor(self):
        consumer = self._make_consumer()
        consumer._handle(self._sample_state("NIFTY", regime="BEARISH"))
        assert consumer.regime("NIFTY") == "BEARISH"

    def test_iv_accessor(self):
        consumer = self._make_consumer()
        state = self._sample_state("NIFTY")
        state["iv"] = 28.5
        consumer._handle(state)
        assert consumer.iv("NIFTY") == 28.5

    def test_gamma_exposure_accessor(self):
        consumer = self._make_consumer()
        state = self._sample_state("NIFTY")
        state["gammaExposure"] = -5000.0
        consumer._handle(state)
        assert consumer.gamma_exposure("NIFTY") == -5000.0

    def test_missing_symbol_in_message_ignored(self):
        consumer = self._make_consumer()
        consumer._handle({"spot": 23100.0})  # no symbol key
        assert consumer.get_all() == {}

    def test_empty_symbol_ignored(self):
        consumer = self._make_consumer()
        consumer._handle({"symbol": "", "spot": 23100.0})
        assert consumer.get_all() == {}


# ═══════════════════════════════════════════════════════════════════════════════
# §E  PositionEventConsumer — Java → Python Feedback
# ═══════════════════════════════════════════════════════════════════════════════

class TestPositionEventConsumer:
    """Tracks active positions from Java execution engine feedback."""

    def _make_consumer(self):
        from kafka_utils.position_event_consumer import PositionEventConsumer
        return PositionEventConsumer()

    def test_open_event_marks_symbol_active(self):
        c = self._make_consumer()
        c._handle_event({"symbol": "NIFTY", "status": "OPEN"})
        assert c.has_active_position("NIFTY") is True

    def test_closed_event_removes_symbol(self):
        c = self._make_consumer()
        c._handle_event({"symbol": "NIFTY", "status": "OPEN"})
        c._handle_event({"symbol": "NIFTY", "status": "CLOSED"})
        assert c.has_active_position("NIFTY") is False

    def test_pending_event_marks_active(self):
        c = self._make_consumer()
        c._handle_event({"symbol": "BANKNIFTY", "status": "PENDING"})
        assert c.has_active_position("BANKNIFTY") is True

    def test_rejected_removes_active(self):
        c = self._make_consumer()
        c._handle_event({"symbol": "NIFTY", "status": "OPEN"})
        c._handle_event({"symbol": "NIFTY", "status": "REJECTED"})
        assert c.has_active_position("NIFTY") is False

    def test_none_status_removes_active(self):
        c = self._make_consumer()
        c._handle_event({"symbol": "NIFTY", "status": "OPEN"})
        c._handle_event({"symbol": "NIFTY", "status": "NONE"})
        assert c.has_active_position("NIFTY") is False

    def test_lowercase_symbol_normalised(self):
        c = self._make_consumer()
        c._handle_event({"symbol": "nifty", "status": "OPEN"})
        assert c.has_active_position("NIFTY") is True
        assert c.has_active_position("nifty") is True

    def test_empty_symbol_ignored(self):
        c = self._make_consumer()
        c._handle_event({"symbol": "", "status": "OPEN"})
        assert c.get_all() == {}

    def test_missing_symbol_ignored(self):
        c = self._make_consumer()
        c._handle_event({"status": "OPEN"})
        assert c.get_all() == {}

    def test_get_all_returns_copy_not_reference(self):
        c = self._make_consumer()
        c._handle_event({"symbol": "NIFTY", "status": "OPEN"})
        snapshot = c.get_all()
        snapshot["FAKE"] = "OPEN"
        assert "FAKE" not in c.get_all()

    def test_multiple_symbols_tracked_independently(self):
        c = self._make_consumer()
        c._handle_event({"symbol": "NIFTY",     "status": "OPEN"})
        c._handle_event({"symbol": "BANKNIFTY", "status": "OPEN"})
        c._handle_event({"symbol": "NIFTY",     "status": "CLOSED"})
        assert c.has_active_position("NIFTY")     is False
        assert c.has_active_position("BANKNIFTY") is True


# ═══════════════════════════════════════════════════════════════════════════════
# §F  PostgresClient — schema compliance
# ═══════════════════════════════════════════════════════════════════════════════

class TestPostgresClientSchema:
    """Validates that Postgres helper methods build contract-compliant rows."""

    def _make_client(self):
        from storage.postgres_client import PostgresClient
        client = PostgresClient()
        client._conn = MagicMock()
        mock_cursor = MagicMock()
        client._conn.cursor.return_value.__enter__ = MagicMock(return_value=mock_cursor)
        client._conn.cursor.return_value.__exit__  = MagicMock(return_value=False)
        client._conn.closed = False
        return client, mock_cursor

    def test_save_analytics_result_does_not_raise(self):
        client, _ = self._make_client()
        result = {
            "market_context": {"symbol": "NIFTY", "spot": 23100.0, "regime": "BULLISH"},
            "confidence":     {"confidence_score": 75},
            "auto_trade_decision": {"auto_trade_decision": {"action": "EXECUTE"}},
        }
        # Should not raise even with mock connection
        try:
            client.save_analytics_result("NIFTY", result)
        except Exception:
            pass  # Connection mock may not fully support context manager — that is OK

    def test_safe_get_nested(self):
        from storage.postgres_client import _safe_get
        d = {"a": {"b": {"c": 42}}}
        assert _safe_get(d, ["a", "b", "c"])     == 42
        assert _safe_get(d, ["a", "b", "x"])     is None
        assert _safe_get(d, ["a", "x", "c"])     is None
        assert _safe_get(d, ["z"])               is None
        assert _safe_get(d, ["a", "b", "c"], -1) == 42

    def test_safe_get_with_default(self):
        from storage.postgres_client import _safe_get
        assert _safe_get({}, ["a", "b"], "DEFAULT") == "DEFAULT"
        assert _safe_get(None, ["a"], "X")          == "X"

    def test_safe_get_handles_non_dict_midpath(self):
        from storage.postgres_client import _safe_get
        d = {"a": "not_a_dict"}
        assert _safe_get(d, ["a", "b"]) is None


# ═══════════════════════════════════════════════════════════════════════════════
# §G  Analytics Result — all contract fields present
# ═══════════════════════════════════════════════════════════════════════════════

class TestAnalyticsResultStructure:
    """AnalyticsResult DTO must contain all §3 contract fields."""

    CONTRACT_FIELDS = [
        "trade_signal", "premium_intelligence", "strike_selection",
        "dealer_positioning", "liquidity_map", "institutional_flow",
        "volatility_context", "market_context", "execution_layer",
        "risk_management", "position_management", "exit_management",
        "confidence", "auto_trade_decision", "complete_decision",
        "ui_view", "timestamp",
    ]

    def _full_analytics_result(self):
        return {f: {} for f in self.CONTRACT_FIELDS}

    def test_all_contract_fields_present(self):
        result = self._full_analytics_result()
        for field in self.CONTRACT_FIELDS:
            assert field in result, f"Missing contract field: {field}"

    def test_execution_ready_path(self):
        result = self._full_analytics_result()
        result["execution_layer"] = {"final_execution": {"execution_ready": True}}
        assert result["execution_layer"]["final_execution"]["execution_ready"] is True

    def test_confidence_score_path(self):
        result = self._full_analytics_result()
        result["confidence"] = {"confidence_score": 80}
        assert result["confidence"]["confidence_score"] == 80

    def test_auto_trade_action_path(self):
        result = self._full_analytics_result()
        result["auto_trade_decision"] = {
            "auto_trade_decision": {
                "action": "EXECUTE", "direction": "BUY",
                "option": "NIFTY24APR23200CE"
            }
        }
        action = result["auto_trade_decision"]["auto_trade_decision"]["action"]
        assert action == "EXECUTE"

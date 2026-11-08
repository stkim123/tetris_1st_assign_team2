package kr.ac.jbnu.se.tetris.network.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.ac.jbnu.se.tetris.battle.BattleEvent;

/** 채굴 완료의 확정 좌표와 아이템 슬롯만 전송한다. 기존 서버의 무이벤트 스냅샷도 허용한다. */
final class WireItemEvents {
    private WireItemEvents() { }

    static List<Object> encode(List<BattleEvent> events) throws IOException {
        List<Object> result = new ArrayList<Object>();
        if (events.size() > 32) throw new IOException("Too many item events");
        for (BattleEvent event : events) {
            if (event.getType() != BattleEvent.Type.ITEM_ACQUIRED
                    || event.getItemSourceX() < 0 || event.getItemSourceY() < 0
                    || event.getItemSlotIndex() < 0)
                throw new IOException("Only confirmed ore pickups are supported");
            Map<String, Object> value = new LinkedHashMap<String, Object>();
            value.put("eventId", event.getEventId());
            value.put("actorId", WireCodec.id(event.getActorId()));
            value.put("itemId", WireCodec.id(event.getReason()));
            value.put("x", event.getItemSourceX());
            value.put("y", event.getItemSourceY());
            value.put("slot", event.getItemSlotIndex());
            result.add(value);
        }
        return result;
    }

    static List<BattleEvent> decode(Object raw) throws IOException {
        List<Object> entries = WireCodec.array(raw);
        if (entries.size() > 32) throw new IOException("Too many item events");
        List<BattleEvent> events = new ArrayList<BattleEvent>();
        long previous = 0;
        for (Object rawEntry : entries) {
            Map<String, Object> value = WireCodec.object(rawEntry);
            WireCodec.keys(value, new String[] { "eventId", "actorId", "itemId", "x", "y", "slot" },
                    new String[0]);
            long id = WireCodec.positive(value.get("eventId"));
            if (id <= previous) throw new IOException("Item events out of order");
            previous = id;
            String actor = WireCodec.id(value.get("actorId"));
            String item = WireCodec.id(value.get("itemId"));
            int x = WireCodec.integer(value.get("x"));
            int y = WireCodec.integer(value.get("y"));
            int slot = WireCodec.integer(value.get("slot"));
            if (x < 0 || x >= 10 || y < 0 || y >= 22 || slot < 0 || slot >= 4)
                throw new IOException("Invalid ore pickup coordinates");
            events.add(BattleEvent.fromWire(BattleEvent.Type.ITEM_ACQUIRED, id, actor, actor,
                    1, item, null, x, y, slot));
        }
        return events;
    }
}

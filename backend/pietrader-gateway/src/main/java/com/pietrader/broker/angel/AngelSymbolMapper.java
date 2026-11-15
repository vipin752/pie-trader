package com.pietrader.broker.angel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AngelSymbolMapper {

    private List<Map<String, Object>> instruments = new ArrayList<>();

    // ================= LOAD FILE =================
    @PostConstruct
    public void load() {
	try {
	    ObjectMapper mapper = new ObjectMapper();

	    InputStream is = getClass()
		    .getClassLoader()
		    .getResourceAsStream("angel/OpenAPIScripMaster.json");

	    instruments = mapper.readValue(is,
		    new TypeReference<List<Map<String, Object>>>() {});

	    log.info("Loaded {} instruments", instruments.size());

	} catch (Exception e) {
	    log.error("Failed to load symbol master", e);
	}
    }

    // ================= FIND TOKEN =================
    public Map<String, String> findOption(
	    String symbol,
	    String strike,
	    String type
    ) {

	// Example: NIFTY, 23000, PE

	List<Map<String, Object>> filtered =
		instruments.stream()
			.filter(i ->
				"NFO".equals(i.get("exch_seg")) &&
					symbol.equalsIgnoreCase((String) i.get("name")) &&
					type.equalsIgnoreCase((String) i.get("symbol").toString().substring(i.get("symbol").toString().length() - 2))
			)
			.collect(Collectors.toList());

	// find closest strike
	for (Map<String, Object> i : filtered) {

	    String strikeVal = (String) i.get("strike");

	    if (strikeVal != null && strikeVal.startsWith(strike)) {

		Map<String, String> result = new HashMap<>();
		result.put("symboltoken", (String) i.get("token"));
		result.put("tradingsymbol", (String) i.get("symbol"));

		return result;
	    }
	}

	return null;
    }
}

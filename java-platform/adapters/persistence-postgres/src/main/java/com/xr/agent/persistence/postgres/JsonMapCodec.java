package com.xr.agent.persistence.postgres;

import java.util.Map;

public interface JsonMapCodec {

    String toJson(Map<String, Object> value);

    Map<String, Object> fromJson(String json);
}

package com.xr.agent.gateway;

import java.util.Iterator;
import java.util.Map;

final class JsonValueCodec {

    private JsonValueCodec() {
    }

    static String toJson(Object value) {
        StringBuilder output = new StringBuilder();
        write(value, output);
        return output.toString();
    }

    private static void write(Object value, StringBuilder output) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String || value instanceof Character || value instanceof Enum<?>) {
            writeString(value.toString(), output);
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<?, ?> entry = entries.next();
                writeString(String.valueOf(entry.getKey()), output);
                output.append(':');
                write(entry.getValue(), output);
                if (entries.hasNext()) {
                    output.append(',');
                }
            }
            output.append('}');
        } else if (value instanceof Iterable<?> values) {
            output.append('[');
            Iterator<?> iterator = values.iterator();
            while (iterator.hasNext()) {
                write(iterator.next(), output);
                if (iterator.hasNext()) {
                    output.append(',');
                }
            }
            output.append(']');
        } else {
            writeString(value.toString(), output);
        }
    }

    private static void writeString(String value, StringBuilder output) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) {
                        output.append(String.format("\\u%04x", (int) character));
                    } else {
                        output.append(character);
                    }
                }
            }
        }
        output.append('"');
    }
}

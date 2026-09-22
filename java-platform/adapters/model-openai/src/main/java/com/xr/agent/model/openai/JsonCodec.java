package com.xr.agent.model.openai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonCodec {

    private JsonCodec() {
    }

    static Map<String, Object> parseObject(String json) {
        Object value = new Parser(json).parse();
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("JSON value must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
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
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                writeString(String.valueOf(entry.getKey()), output);
                output.append(':');
                write(entry.getValue(), output);
            }
            output.append('}');
        } else if (value instanceof Iterable<?> values) {
            output.append('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) {
                    output.append(',');
                }
                first = false;
                write(item, output);
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

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input == null ? "" : input;
        }

        private Object parse() {
            skipWhitespace();
            Object result = parseValue();
            skipWhitespace();
            if (index != input.length()) {
                throw error("Unexpected trailing content");
            }
            return result;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= input.length()) {
                throw error("JSON value is missing");
            }
            return switch (input.charAt(index)) {
                case '{' -> parseObjectValue();
                case '[' -> parseArrayValue();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObjectValue() {
            Map<String, Object> object = new LinkedHashMap<>();
            index++;
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            while (true) {
                String key = parseString();
                skipWhitespace();
                require(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) {
                    return object;
                }
                require(',');
                skipWhitespace();
            }
        }

        private List<Object> parseArrayValue() {
            List<Object> values = new ArrayList<>();
            index++;
            skipWhitespace();
            if (consume(']')) {
                return values;
            }
            while (true) {
                values.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    return values;
                }
                require(',');
            }
        }

        private String parseString() {
            require('"');
            StringBuilder value = new StringBuilder();
            while (index < input.length()) {
                char character = input.charAt(index++);
                if (character == '"') {
                    return value.toString();
                }
                if (character == '\\') {
                    if (index >= input.length()) {
                        throw error("String escape is incomplete");
                    }
                    char escaped = input.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> value.append(escaped);
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> value.append(parseUnicodeEscape());
                        default -> throw error("Unsupported string escape");
                    }
                } else {
                    value.append(character);
                }
            }
            throw error("String is not closed");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > input.length()) {
                throw error("Unicode escape is incomplete");
            }
            String hex = input.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                throw error("Invalid unicode escape");
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!input.startsWith(literal, index)) {
                throw error("Invalid literal");
            }
            index += literal.length();
            return value;
        }

        private Number parseNumber() {
            int start = index;
            if (consume('-')) {
                requireDigit();
            }
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            boolean decimal = false;
            if (consume('.')) {
                decimal = true;
                requireDigit();
                while (index < input.length() && Character.isDigit(input.charAt(index))) {
                    index++;
                }
            }
            if (index < input.length() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) {
                    index++;
                }
                requireDigit();
                while (index < input.length() && Character.isDigit(input.charAt(index))) {
                    index++;
                }
            }
            String value = input.substring(start, index);
            try {
                return decimal ? Double.parseDouble(value) : Long.parseLong(value);
            } catch (NumberFormatException exception) {
                throw error("Invalid number");
            }
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private boolean consume(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void require(char expected) {
            if (!consume(expected)) {
                throw error("Expected '" + expected + "'");
            }
        }

        private void requireDigit() {
            if (index >= input.length() || !Character.isDigit(input.charAt(index))) {
                throw error("Expected digit");
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + index);
        }
    }
}

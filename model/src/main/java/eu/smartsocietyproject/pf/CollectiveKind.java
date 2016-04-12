package eu.smartsocietyproject.pf;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Map;

public final class CollectiveKind {
    private final String id;
    private final Map<String, Attribute> defaultValues;

    private CollectiveKind(String id, Map<String, Attribute> defaultValues) {
        this.id = id;
        this.defaultValues = ImmutableMap.copyOf(defaultValues);
    }

    public String getId() {
        return id;
    }

    public Map<String, Attribute> getDefaultValues() {
        return Maps.newHashMap(defaultValues);
    }

    public boolean isAttributeValid(String name, Attribute value) {
        Attribute defaultValue = defaultValues.get(name);
        return
            defaultValue != null &&
            defaultValues.get(name).getClass().isInstance(value);
    }

    public boolean areAttributesValid(Map<String, Attribute> attributes) {
        return
            attributes
                .entrySet()
                .stream()
                .allMatch(e -> isAttributeValid(e.getKey(), e.getValue()));
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private final HashMap<String, Attribute> defaults = new HashMap<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder addAttribute(String attributeName, Attribute defaultValue) {
            defaults.put(attributeName, defaultValue);
            return this;
        }

        public CollectiveKind build() {
            return new CollectiveKind(id, defaults);
        }
    }
}

package io.microconfig.properties.resolver.placeholder;

import io.microconfig.environments.Component;
import io.microconfig.properties.Property;

import java.util.Optional;

public interface ResolveStrategy {
    Optional<Property> resolve(String key, Component component, String environment);
}
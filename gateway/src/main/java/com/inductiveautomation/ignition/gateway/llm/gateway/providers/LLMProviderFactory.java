package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

import com.inductiveautomation.ignition.gateway.llm.gateway.providers.impl.ClaudeProvider;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.impl.OllamaProvider;
import com.inductiveautomation.ignition.gateway.llm.gateway.providers.impl.OpenAIProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing LLM provider instances.
 */
public class LLMProviderFactory {

    private static final Logger logger = LoggerFactory.getLogger(LLMProviderFactory.class);

    private final Map<String, LLMProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, ProviderConfig> configs = new ConcurrentHashMap<>();
    private String defaultProviderId;

    public LLMProviderFactory() {
        logger.info("LLMProviderFactory initialized");
    }

    /**
     * Registers a provider configuration and creates the provider instance.
     */
    public void registerProvider(ProviderConfig config) {
        String providerId = config.getProviderId();

        LLMProvider provider = createProvider(providerId, config);
        if (provider != null) {
            providers.put(providerId, provider);
            configs.put(providerId, config);
            logger.info("Registered LLM provider: {} ({})", providerId, provider.getDisplayName());

            // Set as default if it's the first enabled provider
            if (defaultProviderId == null && config.isEnabled()) {
                defaultProviderId = providerId;
                logger.info("Set default provider: {}", providerId);
            }
        }
    }

    /**
     * Creates a provider instance based on the provider ID.
     */
    private LLMProvider createProvider(String providerId, ProviderConfig config) {
        switch (providerId) {
            case "claude":
                return new ClaudeProvider(config);
            case "openai":
                return new OpenAIProvider(config);
            case "ollama":
                return new OllamaProvider(config);
            default:
                logger.warn("Unknown provider ID: {}", providerId);
                return null;
        }
    }

    /**
     * Gets a provider by ID.
     */
    public Optional<LLMProvider> getProvider(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    /**
     * Gets the default provider.
     */
    public LLMProvider getDefaultProvider() {
        if (defaultProviderId == null) {
            throw new IllegalStateException("No default LLM provider configured");
        }
        LLMProvider provider = providers.get(defaultProviderId);
        if (provider == null) {
            throw new IllegalStateException("Default provider not found: " + defaultProviderId);
        }
        return provider;
    }

    /**
     * Sets the default provider ID.
     */
    public void setDefaultProvider(String providerId) {
        if (!providers.containsKey(providerId)) {
            throw new IllegalArgumentException("Provider not registered: " + providerId);
        }
        this.defaultProviderId = providerId;
        logger.info("Changed default provider to: {}", providerId);
    }

    /**
     * Gets all registered providers.
     */
    public Collection<LLMProvider> getAllProviders() {
        return providers.values();
    }

    /**
     * Gets all available (configured and ready) providers.
     */
    public Collection<LLMProvider> getAvailableProviders() {
        return providers.values().stream()
                .filter(LLMProvider::isAvailable)
                .toList();
    }

    /**
     * Gets the configuration for a provider.
     */
    public Optional<ProviderConfig> getConfig(String providerId) {
        return Optional.ofNullable(configs.get(providerId));
    }

    /**
     * Updates the configuration for a provider.
     */
    public void updateConfig(ProviderConfig config) {
        String providerId = config.getProviderId();

        // Remove old provider
        providers.remove(providerId);
        configs.remove(providerId);

        // Register with new config
        registerProvider(config);
    }

    /**
     * Removes a provider.
     */
    public void removeProvider(String providerId) {
        providers.remove(providerId);
        configs.remove(providerId);

        if (providerId.equals(defaultProviderId)) {
            // Find another provider to be the default
            defaultProviderId = providers.keySet().stream().findFirst().orElse(null);
            if (defaultProviderId != null) {
                logger.info("Changed default provider to: {}", defaultProviderId);
            }
        }

        logger.info("Removed LLM provider: {}", providerId);
    }

    /**
     * Checks if a provider is available.
     */
    public boolean isAvailable(String providerId) {
        LLMProvider provider = providers.get(providerId);
        return provider != null && provider.isAvailable();
    }

    /**
     * Gets the current default provider ID.
     */
    public String getDefaultProviderId() {
        return defaultProviderId;
    }
}

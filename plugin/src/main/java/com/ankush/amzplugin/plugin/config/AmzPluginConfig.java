package com.ankush.amzplugin.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.amazonmusic")
@Component("amzPluginConfig")
public class AmzPluginConfig {
	private String[] providers = { "ytsearch:\"%ISRC%\"", "ytsearch:%QUERY%" };
	private boolean enabled = false;
	private int searchLimit = 10;

	public String[] getProviders() { return providers; }
	public void setProviders(String[] providers) { this.providers = providers; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public int getSearchLimit() { return searchLimit; }
	public void setSearchLimit(int searchLimit) { this.searchLimit = searchLimit; }
}

package com.ankush.amzplugin.plugin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "plugins.nothinglink")
@Component("nothingLinkConfig")
public class NothingLinkConfig {
	private String webhookUrl = "";
	private boolean webhookEnabled = false;
	private String webhookBotName = "NothingLink";
	private boolean logTrackEvents = true;
	private boolean logSearchEvents = false;

	public String getWebhookUrl() {
		return webhookUrl;
	}

	public void setWebhookUrl(String webhookUrl) {
		this.webhookUrl = webhookUrl;
	}

	public boolean isWebhookEnabled() {
		return webhookEnabled;
	}

	public void setWebhookEnabled(boolean webhookEnabled) {
		this.webhookEnabled = webhookEnabled;
	}

	public String getWebhookBotName() {
		return webhookBotName;
	}

	public void setWebhookBotName(String webhookBotName) {
		this.webhookBotName = webhookBotName;
	}

	public boolean isLogTrackEvents() {
		return logTrackEvents;
	}

	public void setLogTrackEvents(boolean logTrackEvents) {
		this.logTrackEvents = logTrackEvents;
	}

	public boolean isLogSearchEvents() {
		return logSearchEvents;
	}

	public void setLogSearchEvents(boolean logSearchEvents) {
		this.logSearchEvents = logSearchEvents;
	}
}
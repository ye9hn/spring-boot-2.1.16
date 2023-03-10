/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.event;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ErrorHandler;

/**
 * {@link SpringApplicationRunListener} to publish {@link SpringApplicationEvent}s.
 * <p>
 * Uses an internal {@link ApplicationEventMulticaster} for the events that are fired
 * before the context is actually refreshed.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Artsiom Yudovin
 * @since 1.0.0
 */
public class EventPublishingRunListener implements SpringApplicationRunListener, Ordered {

	private final SpringApplication application;

	private final String[] args;

	/**
	 * 拥有一个SimpleApplicationEventMulticaster事件广播器来广播事件
	 * EventPublishingRunListener对象没有承担广播事件的职责，
	 * 而最终是委托SimpleApplicationEventMulticaster这个哥们来广播事件的。
	 */
	private final SimpleApplicationEventMulticaster initialMulticaster;

	public EventPublishingRunListener(SpringApplication application, String[] args) {
		this.application = application;
		this.args = args;
		/**
		 * 新建一个事件广播器SimpleApplicationEventMulticaster对象
		 */
		this.initialMulticaster = new SimpleApplicationEventMulticaster();
		// 遍历在构造SpringApplication对象时从spring.factories配置文件中获取的事件监听器
		/**
		 * 可以看到在EventPublishingRunListener的构造函数中有一个for循环
		 * 会遍历之前从spring.factories中加载的监听器们，
		 * 然后添加到集合中缓存起来，用于以后广播各种事件时直接从这个集合中取出来即可，
		 * 而不用再去spring.factories中加载，提高效率
		 */
		for (ApplicationListener<?> listener : application.getListeners()) {
			// 将从spring.factories配置文件中获取的事件监听器们添加到事件广播器initialMulticaster对象的相关集合中
			this.initialMulticaster.addApplicationListener(listener);
		}
	}

	@Override
	public int getOrder() {
		return 0;
	}

	/**
	 * 发射【ApplicationStartingEvent】事件
	 */
	@Override
	public void starting() {
		this.initialMulticaster.multicastEvent(new ApplicationStartingEvent(this.application, this.args));
	}

	/**
	 * 发射【ApplicationEnvironmentPreparedEvent】事件
	 * @param environment the environment
	 */
	@Override
	public void environmentPrepared(ConfigurableEnvironment environment) {
		this.initialMulticaster
				.multicastEvent(new ApplicationEnvironmentPreparedEvent(this.application, this.args, environment));
	}

	/**
	 * 发射【ApplicationContextInitializedEvent】事件
	 * @param context the application context
	 */
	@Override
	public void contextPrepared(ConfigurableApplicationContext context) {
		this.initialMulticaster
				.multicastEvent(new ApplicationContextInitializedEvent(this.application, this.args, context));
	}

	/**
	 * 发射【ApplicationPreparedEvent】事件
	 * @param context the application context
	 */
	@Override
	public void contextLoaded(ConfigurableApplicationContext context) {
		for (ApplicationListener<?> listener : this.application.getListeners()) {
			if (listener instanceof ApplicationContextAware) {
				((ApplicationContextAware) listener).setApplicationContext(context);
			}
			context.addApplicationListener(listener);
		}
		this.initialMulticaster.multicastEvent(new ApplicationPreparedEvent(this.application, this.args, context));
	}

	/**
	 * 发射【ApplicationStartedEvent】事件
	 * @param context the application context.
	 */
	@Override
	public void started(ConfigurableApplicationContext context) {
		context.publishEvent(new ApplicationStartedEvent(this.application, this.args, context));
	}

	/**
	 * 发射【ApplicationReadyEvent】事件
	 * @param context the application context.
	 */
	@Override
	public void running(ConfigurableApplicationContext context) {
		context.publishEvent(new ApplicationReadyEvent(this.application, this.args, context));
	}

	/**
	 * 发射【ApplicationFailedEvent】事件
	 * @param context the application context or {@code null} if a failure occurred before
	 * the context was created
	 * @param exception the failure
	 */
	@Override
	public void failed(ConfigurableApplicationContext context, Throwable exception) {
		ApplicationFailedEvent event = new ApplicationFailedEvent(this.application, this.args, context, exception);
		if (context != null && context.isActive()) {
			// Listeners have been registered to the application context so we should
			// use it at this point if we can
			context.publishEvent(event);
		}
		else {
			// An inactive context may not have a multicaster so we use our multicaster to
			// call all of the context's listeners instead
			if (context instanceof AbstractApplicationContext) {
				for (ApplicationListener<?> listener : ((AbstractApplicationContext) context)
						.getApplicationListeners()) {
					this.initialMulticaster.addApplicationListener(listener);
				}
			}
			this.initialMulticaster.setErrorHandler(new LoggingErrorHandler());
			this.initialMulticaster.multicastEvent(event);
		}
	}

	private static class LoggingErrorHandler implements ErrorHandler {

		private static final Log logger = LogFactory.getLog(EventPublishingRunListener.class);

		@Override
		public void handleError(Throwable throwable) {
			logger.warn("Error calling ApplicationEventListener", throwable);
		}

	}

}

/*
 * Copyright 2010 Robert Fletcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.plugin.asyncevents

import org.springframework.context.ApplicationEvent
import grails.plugin.asyncevents.RetryableFailureException

class TooManyRetriesException extends RuntimeException {

	final ApplicationEvent event

	TooManyRetriesException(ApplicationEventNotification notification, RetryableFailureException cause) {
		super("Exceeded maximum retries of $notification.retryCount when trying to notify listener $notification.target" as String, cause)
		this.event = notification.event
	}
}
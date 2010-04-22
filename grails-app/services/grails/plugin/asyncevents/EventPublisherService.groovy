package grails.plugin.asyncevents

import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationEvent
import org.springframework.util.ErrorHandler
import java.util.concurrent.*
import static java.util.concurrent.TimeUnit.MILLISECONDS
import static java.util.concurrent.TimeUnit.SECONDS

class EventPublisherService implements InitializingBean, DisposableBean {

	static transactional = false

	ErrorHandler errorHandler

	private final Collection<AsyncEventListener> listeners = []
	private final ExecutorService executor = Executors.newSingleThreadExecutor()
	private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor()
	private final BlockingQueue<RetryableEvent> queue = new LinkedBlockingQueue<RetryableEvent>()
	private boolean done = false

	void addListener(AsyncEventListener listener) {
		listeners << listener
	}

	void publishEvent(ApplicationEvent event) {
		listeners.each { AsyncEventListener listener ->
			queue.put(new RetryableEvent(listener, event))
		}
	}

	void afterPropertiesSet() {
		executor.execute {
			while (!done) {
				log.debug "polling queue..."
				RetryableEvent event = queue.poll(250, MILLISECONDS)
				if (event) {
					log.info "got event $event from queue"
					notifyListener(event)
				}
			}
			log.warn "event notifier thread exiting..."
		}
	}

	void destroy() {
		done = true
		shutdown(executor, 1, SECONDS)
		shutdown(retryExecutor, 1, SECONDS)
	}

	private void notifyListener(RetryableEvent event) {
		try {
			def success = event.tryNotifyingListener()
			if (!success) {
				rescheduleNotification(event)
			}
		} catch (Exception e) {
			log.error "Notififying listener $event.target failed", e
			errorHandler?.handleError(e)
		}
	}

	private void rescheduleNotification(RetryableEvent event) {
		log.warn "Notifying listener $event.target failed"
		if (event.shouldRetry()) {
			long retryDelay = event.retryDelayMillis
			log.warn "Will retry in $retryDelay $MILLISECONDS"
			event.incrementRetryCount()
			retryExecutor.schedule(this.&notifyListener.curry(event), retryDelay, MILLISECONDS)
		} else {
			throw new RetriedTooManyTimesException(event)
		}
	}

	private void shutdown(ExecutorService executor, int timeout, TimeUnit unit) {
		executor.shutdown()
		if (!executor.awaitTermination(timeout, unit)) {
			log.warn "Executor still alive $timeout $unit after shutdown, forcing..."
			executor.shutdownNow()
			assert executor.awaitTermination(timeout, unit), "Forced shutdown of executor incomplete after $timeout $unit."
		}
	}
}

interface Retryable {

	void incrementRetryCount()

	long getRetryDelayMillis()

	boolean shouldRetry()
}

class RetryableEvent implements Retryable {

	private int retryCount = 0
	final AsyncEventListener target
	final ApplicationEvent event

	RetryableEvent(AsyncEventListener target, ApplicationEvent event) {
		this.target = target
		this.event = event
	}

	boolean tryNotifyingListener() {
		return target.onApplicationEvent(event)
	}

	void incrementRetryCount() {
		retryCount++
	}

	long getRetryDelayMillis() {
		long retryDelay = target.retryDelay
		retryCount.times {
			retryDelay *= 2
		}
		return retryDelay
	}

	boolean shouldRetry() {
		return target.maxRetries == AsyncEventListener.UNLIMITED_RETRIES || retryCount < target.maxRetries
	}

}

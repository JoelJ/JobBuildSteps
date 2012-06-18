package com.attask.jenkins;

/**
 * User: joeljohnson
 * Date: 3/6/12
 * Time: 6:31 PM
 */
public class Waiter {
	private final int retries;
	private final int delay;

	public Waiter(int retries, int delay) {
		this.retries = retries;
		this.delay = delay;
	}

	public boolean retryUntil(Predicate callable) {
		boolean finished = false;
		for (int i = 0; retries == 0 || i < retries; i++) {
			if (callable.call()) {
				finished = true;
				break;
			} else {
				try {
					Thread.sleep(delay);
				} catch (InterruptedException e) {
					//if the thread has been interrupted, finish things up.
					break;
				}
			}
		}
		return finished;
	}

	public static interface Predicate {
		public boolean call();
	}
}

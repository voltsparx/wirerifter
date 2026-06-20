use std::panic::{self, AssertUnwindSafe};
use std::sync::{mpsc, Arc, Mutex};
use std::thread;

/// Work unit that can be transferred across worker boundaries.
type Job = Box<dyn FnOnce() + Send + 'static>;

/// Multithreaded parallel executor to handle asynchronous payload scanning safely.
pub struct ThreadPool {
    workers: Vec<Worker>,
    sender: Option<mpsc::Sender<Job>>,
}

impl ThreadPool {
    /// Creates a new ThreadPool with a set amount of threads.
    pub fn new(size: usize) -> ThreadPool {
        let size = size.max(1);

        let (sender, receiver) = mpsc::channel();
        let receiver = Arc::new(Mutex::new(receiver));
        let mut workers = Vec::with_capacity(size);

        for _ in 0..size {
            workers.push(Worker::new(Arc::clone(&receiver)));
        }

        ThreadPool {
            workers,
            sender: Some(sender),
        }
    }

    /// Submits a parallel task to the worker queue.
    pub fn execute<F>(&self, f: F)
    where
        F: FnOnce() + Send + 'static,
    {
        let _ = self.try_execute(f);
    }

    /// Submits a task and reports whether it entered the queue.
    pub fn try_execute<F>(&self, f: F) -> bool
    where
        F: FnOnce() + Send + 'static,
    {
        let job = Box::new(f);
        if let Some(ref sender) = self.sender {
            sender.send(job).is_ok()
        } else {
            false
        }
    }

    pub fn worker_count(&self) -> usize {
        self.workers.len()
    }
}

impl Drop for ThreadPool {
    fn drop(&mut self) {
        drop(self.sender.take());

        for worker in &mut self.workers {
            if let Some(thread) = worker.thread.take() {
                let _ = thread.join();
            }
        }
    }
}

struct Worker {
    thread: Option<thread::JoinHandle<()>>,
}

impl Worker {
    fn new(receiver: Arc<Mutex<mpsc::Receiver<Job>>>) -> Worker {
        let thread = thread::spawn(move || loop {
            let message = match receiver.lock() {
                Ok(guard) => guard.recv(),
                Err(_) => break,
            };

            match message {
                Ok(job) => {
                    let _ = panic::catch_unwind(AssertUnwindSafe(job));
                }
                Err(_) => {
                    // Channel disconnected, terminate thread safely.
                    break;
                }
            }
        });

        Worker { thread: Some(thread) }
    }
}

#[cfg(test)]
mod tests {
    use super::ThreadPool;
    use std::sync::atomic::{AtomicUsize, Ordering};
    use std::sync::Arc;
    use std::thread;
    use std::time::Duration;

    #[test]
    fn zero_size_pool_is_coerced_to_one_worker() {
        let pool = ThreadPool::new(0);
        assert_eq!(pool.worker_count(), 1);
    }

    #[test]
    fn worker_survives_panicking_job() {
        let pool = ThreadPool::new(1);
        let count = Arc::new(AtomicUsize::new(0));
        pool.execute(|| panic!("isolated worker panic"));
        let count_for_job = Arc::clone(&count);
        assert!(pool.try_execute(move || {
            count_for_job.fetch_add(1, Ordering::SeqCst);
        }));
        thread::sleep(Duration::from_millis(80));
        assert_eq!(count.load(Ordering::SeqCst), 1);
    }
}

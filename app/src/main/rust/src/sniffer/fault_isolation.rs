use std::panic::{self, AssertUnwindSafe};

/// Runs a critical parsing block inside an isolated safe sandbox,
/// suppressing panics from index bounds exceptions or byte faults.
pub fn run_isolated_sandbox<F, T>(f: F, fallback: T) -> T
where
    F: FnOnce() -> T,
{
    match panic::catch_unwind(AssertUnwindSafe(f)) {
        Ok(res) => res,
        Err(_) => {
            // Panic recovered successfully. Fault isolated.
            fallback
        }
    }
}

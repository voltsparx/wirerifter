use rusqlite::{params, Connection, Result};
use std::time::Duration;

/// Struct representation of a packet stored in the SQLite database.
#[derive(Debug, Clone)]
pub struct NativePacketRecord {
    pub timestamp: i64,
    pub source_ip: String,
    pub dest_ip: String,
    pub protocol: String,
    pub source_port: Option<i32>,
    pub dest_port: Option<i32>,
    pub length: i32,
    pub is_suspicious: bool,
    pub threat_tags: String,
    pub entropy: f64,
    pub heuristic_class: String,
}

/// Native Persistence Engine wrapping SQLite connection layers.
// Wasted 3hrs of my life here, lol
pub struct SQLitePersistenceEngine {
    db_path: String,
}

impl SQLitePersistenceEngine {
    /// Instantiates a database session at the requested destination path.
    pub fn new(db_path: &str) -> Result<Self> {
        let engine = SQLitePersistenceEngine {
            db_path: db_path.to_string(),
        };
        engine.initialize_schema()?;
        Ok(engine)
    }

    /// Prepares table architectures if they are not already instantiated.
    fn initialize_schema(&self) -> Result<()> {
        let conn = Connection::open(&self.db_path)?;
        conn.busy_timeout(Duration::from_secs(2))?;
        conn.pragma_update(None, "journal_mode", "WAL")?;
        conn.pragma_update(None, "synchronous", "NORMAL")?;
        conn.execute(
            "CREATE TABLE IF NOT EXISTS native_pcap_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                source_ip TEXT NOT NULL,
                dest_ip TEXT NOT NULL,
                protocol TEXT NOT NULL,
                source_port INTEGER,
                dest_port INTEGER,
                length INTEGER NOT NULL,
                is_suspicious INTEGER NOT NULL,
                threat_tags TEXT NOT NULL,
                entropy REAL NOT NULL DEFAULT 0,
                heuristic_class TEXT NOT NULL DEFAULT ''
            );",
            [],
        )?;
        self.ensure_column(&conn, "source_port", "INTEGER")?;
        self.ensure_column(&conn, "dest_port", "INTEGER")?;
        self.ensure_column(&conn, "entropy", "REAL NOT NULL DEFAULT 0")?;
        self.ensure_column(&conn, "heuristic_class", "TEXT NOT NULL DEFAULT ''")?;
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_native_pcap_logs_time ON native_pcap_logs(timestamp DESC);",
            [],
        )?;
        conn.execute(
            "CREATE INDEX IF NOT EXISTS idx_native_pcap_logs_suspicious ON native_pcap_logs(is_suspicious, timestamp DESC);",
            [],
        )?;
        Ok(())
    }

    fn ensure_column(&self, conn: &Connection, name: &str, definition: &str) -> Result<()> {
        let mut stmt = conn.prepare("PRAGMA table_info(native_pcap_logs)")?;
        let names = stmt
            .query_map([], |row| row.get::<_, String>(1))?
            .collect::<Result<Vec<_>>>()?;
        if !names.iter().any(|column| column == name) {
            conn.execute(
                &format!("ALTER TABLE native_pcap_logs ADD COLUMN {} {}", name, definition),
                [],
            )?;
        }
        Ok(())
    }

    /// Persists a packet record directly into SQLite.
    pub fn save_packet_record(&self, record: &NativePacketRecord) -> Result<()> {
        let conn = Connection::open(&self.db_path)?;
        conn.busy_timeout(Duration::from_secs(2))?;
        conn.execute(
            "INSERT INTO native_pcap_logs (
                timestamp, source_ip, dest_ip, protocol, source_port, dest_port,
                length, is_suspicious, threat_tags, entropy, heuristic_class
             )
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
            params![
                record.timestamp,
                record.source_ip,
                record.dest_ip,
                record.protocol,
                record.source_port,
                record.dest_port,
                record.length,
                if record.is_suspicious { 1 } else { 0 },
                record.threat_tags,
                record.entropy,
                record.heuristic_class
            ],
        )?;
        Ok(())
    }

    /// Pulls the latest records for diagnostics logs.
    pub fn fetch_latest_logs(&self, limit: usize) -> Result<Vec<NativePacketRecord>> {
        let conn = Connection::open(&self.db_path)?;
        let mut stmt = conn.prepare(
            "SELECT timestamp, source_ip, dest_ip, protocol, source_port, dest_port,
                    length, is_suspicious, threat_tags, entropy, heuristic_class
             FROM native_pcap_logs ORDER BY id DESC LIMIT ?1"
        )?;
        
        let log_iter = stmt.query_map([limit], |row| {
            let is_susp_int: i32 = row.get(7)?;
            Ok(NativePacketRecord {
                timestamp: row.get(0)?,
                source_ip: row.get(1)?,
                dest_ip: row.get(2)?,
                protocol: row.get(3)?,
                source_port: row.get(4)?,
                dest_port: row.get(5)?,
                length: row.get(6)?,
                is_suspicious: is_susp_int != 0,
                threat_tags: row.get(8)?,
                entropy: row.get(9)?,
                heuristic_class: row.get(10)?,
            })
        })?;

        let mut list = Vec::new();
        for log in log_iter {
            list.push(log?);
        }
        Ok(list)
    }
}
